//
// Created by mj on 11/12/25.
//

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include "room.h"
#include "protocol.h"
#include "commands.h"
#include "reliability.h"
#include "game_logic.h"
#include "config.h"

int init_room_manager(RoomManager *manager, ServerConfig *config) {
    if (!manager || !config) return 0;

    manager->count = 1 + config->room_count;
    manager->rooms = calloc(manager->count, sizeof(Room));
    if (!manager->rooms) return 0;

    manager->rooms[LOBBY_ID].id = LOBBY_ID;
    manager->rooms[LOBBY_ID].max_players = config->max_lobby_players;
    manager->rooms[LOBBY_ID].winning_score = 0;
    manager->rooms[LOBBY_ID].player_count = 0;
    manager->rooms[LOBBY_ID].is_game_active = false;
    manager->rooms[LOBBY_ID].current_player_index = -1;
    manager->rooms[LOBBY_ID].running = 0;
    manager->rooms[LOBBY_ID].is_vote_active = false;
    manager->rooms[LOBBY_ID].vote_kick_count = 0;
    manager->rooms[LOBBY_ID].vote_wait_count = 0;
    memset(manager->rooms[LOBBY_ID].vote_target_name, 0, sizeof(manager->rooms[LOBBY_ID].vote_target_name));
    if (!init_message_queue(&manager->rooms[LOBBY_ID].queue)) {
        free(manager->rooms);
        return 0;
    }

    for (int i = 0; i < config->room_count; i++) {
        int room_id = i + 1;
        manager->rooms[room_id].id = room_id;
        manager->rooms[room_id].max_players = config->rooms[i].max_players;
        manager->rooms[room_id].winning_score = config->rooms[i].winning_score;
        manager->rooms[room_id].player_count = 0;
        manager->rooms[room_id].is_game_active = false;
        manager->rooms[room_id].current_player_index = -1;
        manager->rooms[room_id].running = 0;
        manager->rooms[room_id].is_vote_active = false;
        manager->rooms[room_id].vote_kick_count = 0;
        manager->rooms[room_id].vote_wait_count = 0;
        memset(manager->rooms[room_id].vote_target_name, 0, sizeof(manager->rooms[room_id].vote_target_name));
        if (!init_message_queue(&manager->rooms[room_id].queue)) {
            for (int j = 0; j <= i; j++) {
                destroy_message_queue(&manager->rooms[j].queue);
            }
            free(manager->rooms);
            return 0;
        }
    }

    return 1;
}

void free_room_manager(RoomManager *manager) {
    if (!manager) {
        return;
    }
    if (manager->rooms) {
        for (int i = 0; i < manager->count; i++) {
            destroy_message_queue(&manager->rooms[i].queue);
        }
        free(manager->rooms);
        manager->rooms = NULL;
    }
    manager->count = 0;
}

int init_dynamic_room(Room *room, int room_id, int max_players, int winning_score, int server_sock, PlayerManager *pm, RoomManager *rm) {
    if (!room) return 0;

    if (room->running) {
        room->running = 0;
        signal_queue_shutdown(&room->queue);
        pthread_join(room->thread_id, NULL);
        destroy_message_queue(&room->queue);
    }

    room->id = room_id;
    room->max_players = max_players;
    room->winning_score = winning_score;
    room->player_count = 0;
    room->is_game_active = false;
    room->current_player_index = -1;
    room->is_vote_active = false;
    room->vote_kick_count = 0;
    room->vote_wait_count = 0;
    memset(room->vote_target_name, 0, sizeof(room->vote_target_name));

    if (!init_message_queue(&room->queue)) {
        return 0;
    }

    room->server_sock = server_sock;
    room->player_manager = pm;
    room->room_manager = rm;
    room->running = 1;

    if (pthread_create(&room->thread_id, NULL, room_thread_func, room) != 0) {
        perror("pthread_create for dynamic room");
        destroy_message_queue(&room->queue);
        room->running = 0;
        return 0;
    }

    return 1;
}

struct RoomManager* roomManager_alloc(ServerConfig * config) {
    struct RoomManager *manager = malloc(sizeof (RoomManager));
    if (!manager) {
        return NULL;
    }
    if (!init_room_manager((RoomManager*)manager, config)) {
        free(manager);
        return NULL;
    }
    return manager;
}

static void process_room_packet(Room *room, Packet *packet) {
    Message msg;
    if (!parse_message(packet->data, packet->length, &msg)) {
        return;
    }

    pthread_mutex_lock(&room->player_manager->mutex);
    Player *player = find_player(room->player_manager, &packet->sender);
    if (player) {

        if (player->long_inactive) {

            char recovery_msg[128];
            snprintf(recovery_msg, sizeof(recovery_msg), "JOINED %s", player->name);

            for (int i = 0; i < room->player_manager->count; i++) {
                Player *other = room->player_manager->Players[i];
                if (other->room_id == room->id && !other->is_disconnected) {
                    send_game_message(room->server_sock, &other->addr, CMD_VOTE, recovery_msg);
                }
            }
        }

        player->last_active_time = time(NULL);
        player->long_inactive = false;
        player->is_disconnected = false;
    }
    pthread_mutex_unlock(&room->player_manager->mutex);

    switch (msg.command) {
        case CMD_JOIN_LOBBY:
            handle_join_lobby(room->player_manager, room->room_manager, &msg, &packet->sender, room->server_sock);
            break;
        case CMD_JOIN_ROOM:
            handle_join_room(room->player_manager, room->room_manager, &msg, &packet->sender, room->server_sock);
            break;
        case CMD_RETURN_TO_LOBBY:
            handle_return_to_lobby(room->player_manager, room->room_manager, &msg, &packet->sender, room->server_sock);
            break;
        case CMD_DISCONNECT:
            handle_disconnect(room->player_manager, room->room_manager, &msg, &packet->sender, room->server_sock);
            break;
        case CMD_VOTE:
            handle_vote(room->player_manager, room->room_manager, &msg, &packet->sender, room->server_sock);
            break;
        case CMD_ACK:
            handle_ack(room->player_manager, &msg, &packet->sender);
            break;
        case CMD_PING:
            break;
        case CMD_GAME_PASS:
        case CMD_GAME_REROLL:
        case CMD_GAME_WIN:
            handle_game_move(room->player_manager, room->room_manager, &msg, &packet->sender, room->server_sock);
            break;
        default:
            send_game_message(room->server_sock, &packet->sender, msg.command, "ERR:Unknown");
            break;
    }

    free_message(&msg);
}

static void check_inactivity(Room *room) {
    time_t now = time(NULL);

    pthread_mutex_lock(&room->player_manager->mutex);

    for (int i = 0; i < room->player_manager->count; i++) {
        Player *player = room->player_manager->Players[i];

        if (player->room_id != room->id) {
            continue;
        }

        time_t inactive_time = now - player->last_active_time;

        if ((player->long_inactive || player->is_disconnected)) {
            time_t time_since_last_vote = now - player->last_vote_broadcast_time;

            if (time_since_last_vote >= PLAYER_TIMEOUT_SECONDS) {
                char vote_remind_msg[128];
                snprintf(vote_remind_msg, sizeof(vote_remind_msg), "VOTE_INIT %s", player->name);

                for (int j = 0; j < room->player_manager->count; j++) {
                    Player *other = room->player_manager->Players[j];
                    if (other->room_id == room->id && !other->is_disconnected && other != player) {
                        send_game_message(room->server_sock, &other->addr, CMD_VOTE, vote_remind_msg);
                        usleep(NETWORK_DELAY_US);
                    }
                }

                player->last_vote_broadcast_time = now;
            }

            if (player->is_disconnected) {
                continue;
            }
        }

        if (inactive_time >= PLAYER_TIMEOUT_SECONDS && !player->is_disconnected) {
            player->is_disconnected = true;
            player->is_active = false;
            player->last_vote_broadcast_time = now;

            char disconnect_status_msg[128];
            snprintf(disconnect_status_msg, sizeof(disconnect_status_msg),
                     "PLAYER_DISCONNECTED %s", player->name);

            for (int j = 0; j < room->player_manager->count; j++) {
                Player *other = room->player_manager->Players[j];
                if (other->room_id == room->id && !other->is_disconnected && other != player) {
                    send_game_message(room->server_sock, &other->addr, CMD_VOTE, disconnect_status_msg);
                    usleep(NETWORK_DELAY_US);
                }
            }

            if (!room->is_vote_active) {
                room->is_vote_active = true;
                strncpy(room->vote_target_name, player->name, sizeof(room->vote_target_name) - 1);
                room->vote_target_name[sizeof(room->vote_target_name) - 1] = '\0';
                room->vote_kick_count = 0;
                room->vote_wait_count = 0;
            }

            char vote_init_msg[128];
            snprintf(vote_init_msg, sizeof(vote_init_msg), "VOTE_INIT %s", player->name);

            for (int j = 0; j < room->player_manager->count; j++) {
                Player *other = room->player_manager->Players[j];
                if (other->room_id == room->id && !other->is_disconnected) {
                    send_game_message(room->server_sock, &other->addr, CMD_VOTE, vote_init_msg);
                    usleep(NETWORK_DELAY_US);
                }
            }
        }
        else if (inactive_time >= LAG_WARNING_SECONDS && inactive_time < PLAYER_TIMEOUT_SECONDS && player->is_active && !player->long_inactive) {
            player->long_inactive = true;
            player->last_vote_broadcast_time = now;

            char lagging_msg[128];
            snprintf(lagging_msg, sizeof(lagging_msg), "LAGGING %s", player->name);
            printf("[Room %d] Player %s is lagging (%lds inactive). Sending reliable notification.\n",
                   room->id, player->name, (long)inactive_time);

            for (int j = 0; j < room->player_manager->count; j++) {
                Player *other = room->player_manager->Players[j];
                if (other->room_id == room->id && !other->is_disconnected) {
                   
                    send_reliable_message(room->server_sock, other, CMD_VOTE, lagging_msg);
                   
                    usleep(NETWORK_DELAY_US);
                }
            }
        }
        else if (inactive_time >= PING_INTERVAL_SECONDS && inactive_time < LAG_WARNING_SECONDS) {
            char ping_msg[64];
            snprintf(ping_msg, sizeof(ping_msg), "PING %ld", (long)time(NULL));
            send_game_message(room->server_sock, &player->addr, CMD_PING, ping_msg);
        }
    }

    pthread_mutex_unlock(&room->player_manager->mutex);
}

void* room_thread_func(void *arg) {
    Room *room = (Room*)arg;

    while (room->running) {
        Packet *packet = dequeue_packet_timed(&room->queue, 1);

        if (packet) {
            process_room_packet(room, packet);
            free(packet);
        }

        check_inactivity(room);
        check_retransmissions(room);
    }

    return NULL;
}

int start_room_threads(RoomManager *manager, int server_sock, PlayerManager *player_manager) {
    if (!manager || !player_manager) return 0;

    for (int i = 0; i < manager->count; i++) {
        Room *room = &manager->rooms[i];
        room->server_sock = server_sock;
        room->player_manager = player_manager;
        room->room_manager = manager;
        room->running = 1;

        if (pthread_create(&room->thread_id, NULL, room_thread_func, room) != 0) {
            perror("pthread_create");
            for (int j = 0; j < i; j++) {
                manager->rooms[j].running = 0;
                signal_queue_shutdown(&manager->rooms[j].queue);
                pthread_join(manager->rooms[j].thread_id, NULL);
            }
            return 0;
        }
    }

    return 1;
}

void stop_room_threads(RoomManager *manager) {
    if (!manager) return;

    for (int i = 0; i < manager->count; i++) {
        manager->rooms[i].running = 0;
        signal_queue_shutdown(&manager->rooms[i].queue);
    }

    for (int i = 0; i < manager->count; i++) {
        pthread_join(manager->rooms[i].thread_id, NULL);
    }
}

