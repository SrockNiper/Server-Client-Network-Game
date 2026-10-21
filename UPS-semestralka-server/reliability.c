#include "reliability.h"
#include "protocol.h"
#include "player.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <pthread.h>

static int global_msg_id = 1001;

int send_reliable_message(int sock, Player *p, int cmd, const char *content) {
    if (!p || !content) return 0;

    int msg_id = global_msg_id++;

    char formatted_msg[256];
    snprintf(formatted_msg, sizeof(formatted_msg), "%d:%s", msg_id, content);

    p->pending_msg_id = msg_id;
    p->pending_cmd = cmd;
    strncpy(p->pending_msg_buffer, formatted_msg, sizeof(p->pending_msg_buffer) - 1);
    p->pending_msg_buffer[sizeof(p->pending_msg_buffer) - 1] = '\0';
    p->waiting_for_ack = true;

    clock_gettime(CLOCK_MONOTONIC, &p->last_send_time);

    send_game_message(sock, &p->addr, cmd, formatted_msg);


    return 1;
}

int handle_ack(PlayerManager *player_manager, Message *msg, struct sockaddr_in *client_addr) {
    if (!player_manager || !msg || !client_addr) return 0;

    pthread_mutex_lock(&player_manager->mutex);

    Player *player = find_player(player_manager, client_addr);
    if (!player) {
        pthread_mutex_unlock(&player_manager->mutex);
        return 0;
    }

    if (!msg->content || msg->content_length == 0) {
        pthread_mutex_unlock(&player_manager->mutex);
        return 0;
    }

    int ack_id = atoi(msg->content);

    if (player->waiting_for_ack && ack_id == player->pending_msg_id) {
        player->waiting_for_ack = false;
    }

    pthread_mutex_unlock(&player_manager->mutex);
    return 1;
}

void check_retransmissions(Room *room) {
    if (!room || !room->player_manager) return;

    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);

    pthread_mutex_lock(&room->player_manager->mutex);

    for (int i = 0; i < room->player_manager->count; i++) {
        Player *p = room->player_manager->Players[i];

        if (!p->waiting_for_ack || p->room_id != room->id) continue;

        long elapsed_ms = (now.tv_sec - p->last_send_time.tv_sec) * 1000 +
                         (now.tv_nsec - p->last_send_time.tv_nsec) / 1000000;

        if (elapsed_ms >= 500) {
            send_game_message(room->server_sock, &p->addr, p->pending_cmd, p->pending_msg_buffer);
            clock_gettime(CLOCK_MONOTONIC, &p->last_send_time);
        }
    }

    pthread_mutex_unlock(&room->player_manager->mutex);
}

void broadcast_to_room(int server_sock, RoomManager *rm, PlayerManager *pm, int room_id, int cmd, const char *message, struct sockaddr_in *sender_addr) {
    if (!rm || !pm || !message) return;

    for (int i = 0; i < pm->count; i++) {
        Player *p = pm->Players[i];
        if (!p) continue;
        if (p->room_id != room_id) continue;
        if (sender_addr && p->addr.sin_addr.s_addr == sender_addr->sin_addr.s_addr && p->addr.sin_port == sender_addr->sin_port) {
            continue;
        }
        send_game_message(server_sock, &p->addr, cmd, message);
    }
}

void broadcast_reliable(int server_sock, RoomManager *rm, PlayerManager *pm, int room_id, int cmd, const char *message, struct sockaddr_in *sender_addr) {
    if (!rm || !pm || !message) return;

    for (int i = 0; i < pm->count; i++) {
        Player *p = pm->Players[i];
        if (!p) continue;
        if (p->room_id != room_id) continue;
        if (p->is_disconnected) continue;
        if (sender_addr && p->addr.sin_addr.s_addr == sender_addr->sin_addr.s_addr && p->addr.sin_port == sender_addr->sin_port) {
            continue;
        }
        send_reliable_message(server_sock, p, cmd, message);
    }
}

