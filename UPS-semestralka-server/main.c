#include <sys/types.h>
#include <sys/socket.h>
#include <stdio.h>
#include <sys/un.h>
#include <unistd.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
#include <ctype.h>
#include <pthread.h>
#include <time.h>
#include "config.h"
#include "protocol.h"
#include "room.h"
#include "player.h"
#include "commands.h"
#include "message_queue.h"

#define CONFIG_FILE "./../server.cfg"
#define BUFFER_SIZE 4096

int main(void)
{
    ServerConfig config = {
            .server_address = "127.0.0.1",
            .server_port = 12345,
            .room_count = 1,
            .max_lobby_players = 10,
            .max_rooms = 10,
            .rooms = {{.max_players = 2, .winning_score = 5000}}
    };

    if (!load_config(CONFIG_FILE, &config)) {
        printf("[Server] Cannot load config, using default values\n");
    }

    printf("=== Server KOSTKY ===\n");
    printf("[Server] Address: %s:%d\n", config.server_address, config.server_port);
    printf("[Server] Max lobby players: %d\n", config.max_lobby_players);
    printf("[Server] Max rooms: %d (created: %d)\n", config.max_rooms, config.room_count);
    for (int i = 0; i < config.room_count; i++) {
        printf("[Server] Room %d: max %d players, winning score %d\n",
               i + 1,
               config.rooms[i].max_players,
               config.rooms[i].winning_score);
    }
    printf("=====================\n\n");

    int server_sock;
    socklen_t client_addr_len;
    struct sockaddr_in local_addr;
    struct sockaddr_in remote_addr;
    char buffer[BUFFER_SIZE];

    server_sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (server_sock < 0) {
        perror("socket");
        return EXIT_FAILURE;
    }

    memset(&local_addr, 0, sizeof(local_addr));
    local_addr.sin_family = AF_INET;
    local_addr.sin_addr.s_addr = inet_addr(config.server_address);
    local_addr.sin_port = htons(config.server_port);

    if (bind(server_sock, (struct sockaddr *)&local_addr, sizeof(local_addr))) {
        perror("bind");
        close(server_sock);
        return EXIT_FAILURE;
    }

    PlayerManager *player_manager = playerManager_alloc();
    if (!player_manager) {
        printf("[Server] Failed to allocate player manager\n");
        close(server_sock);
        return EXIT_FAILURE;
    }

    RoomManager *room_manager = roomManager_alloc(&config);
    if (!room_manager) {
        printf("[Server] Failed to allocate room manager\n");
        free_player_manager(player_manager);
        close(server_sock);
        return EXIT_FAILURE;
    }

    if (!start_room_threads(room_manager, server_sock, player_manager)) {
        printf("[Server] Failed to start room threads\n");
        free_room_manager(room_manager);
        free_player_manager(player_manager);
        close(server_sock);
        return EXIT_FAILURE;
    }

    printf("[Server] Ready. Listening for packets...\n\n");

    ssize_t n;
    while (1) {
        client_addr_len = sizeof(remote_addr);
        n = recvfrom(server_sock, buffer, sizeof(buffer), 0,
                     (struct sockaddr*)&remote_addr, &client_addr_len);

        if (n < 0) {
            perror("recvfrom");
            continue;
        }

        Room *target_room = NULL;
        Player *player = NULL;

        pthread_mutex_lock(&player_manager->mutex);
        player = find_player(player_manager, &remote_addr);

        if (player && !player->is_disconnected) {
            target_room = &room_manager->rooms[player->room_id];
            pthread_mutex_unlock(&player_manager->mutex);
        }
        else {
            Player *reconnect_player = find_player_by_nickname_in_message(player_manager, buffer, n);

            if (reconnect_player) {
                printf("[Server] Session recovery: %s\n", reconnect_player->name);

                memcpy(&reconnect_player->addr, &remote_addr, sizeof(struct sockaddr_in));
                reconnect_player->is_disconnected = false;
                reconnect_player->is_active = true;
                reconnect_player->last_active_time = time(NULL);
                reconnect_player->long_inactive = false;

                target_room = &room_manager->rooms[reconnect_player->room_id];
                send_game_message(server_sock, &remote_addr, CMD_JOIN_LOBBY, "OK:Reconnected");
            }
            else {
                target_room = &room_manager->rooms[LOBBY_ID];
            }

            pthread_mutex_unlock(&player_manager->mutex);
        }

        if (target_room) {
            if (!enqueue_packet(&target_room->queue, &remote_addr, buffer, (int)n)) {
                fprintf(stderr, "[Server] Failed to enqueue packet to Room %d\n", target_room->id);
            }
        }
    }

    stop_room_threads(room_manager);
    close(server_sock);
    free_player_manager(player_manager);
    free_room_manager(room_manager);

    return 0;
}
