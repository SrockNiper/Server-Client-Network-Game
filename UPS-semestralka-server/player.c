//
// Created by mj on 11/11/25.
//

#include "player.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

int init_player_manager(PlayerManager *manager) {
    if (!manager){
        return 0;
    }
    manager->count = 0;
    memset(manager->Players, 0, sizeof(manager->Players));
    if (pthread_mutex_init(&manager->mutex, NULL) != 0) {
        return 0;
    }
    return 1;
}
struct PlayerManager * playerManager_alloc() {
    struct PlayerManager *manager = malloc(sizeof (PlayerManager));
    if (!manager) {
        return NULL;
    }
    init_player_manager((PlayerManager *) manager);
    return manager;
}
struct Player* player_alloc(const char *name, struct sockaddr_in *addr, int room_id) {
    struct Player *player = malloc (sizeof (Player));
    if (!player) {
        return NULL;
    }
    if (!init_player((Player *) player, name, addr, room_id)) {
        free(player);
        return NULL;
    }
    return player;
}
int init_player(Player *player, const char *name, struct sockaddr_in *addr, int room_id) {
    if (!player || !name || !addr) {
        return 0;
    }
    strncpy(player->name, name, MAX_PLAYER_NAME - 1);
    player->name[MAX_PLAYER_NAME - 1] = '\0';
    memcpy(&player->addr, addr, sizeof(struct sockaddr_in));
    player->room_id = room_id;
    player->is_active = true;
    player->long_inactive = false;
    memset(player->input_buffer, 0, PLAYER_BUFFER_SIZE);
    player->buffer_usage = 0;
    player->last_active_time = time(NULL);
    player->is_disconnected = false;
    player->is_ready = false;
    player->last_vote_broadcast_time = 0;

    player->total_score = 0;
    player->round_score = 0;
    memset(player->current_dice, 0, sizeof(player->current_dice));
    player->active_dice_count = 6;
    player->is_on_turn = false;

    player->pending_msg_id = 0;
    memset(player->pending_msg_buffer, 0, sizeof(player->pending_msg_buffer));
    player->waiting_for_ack = false;
    player->last_send_time.tv_sec = 0;
    player->last_send_time.tv_nsec = 0;

    return 1;
}
int add_player(PlayerManager *manager, const char *name, struct sockaddr_in *addr, int room_id) {
    if (!manager || !name || !addr) {
        return 0;
    }
    if (manager->count >= MAX_PLAYERS) {
        return 0;
    }
    Player *new_player = player_alloc(name, addr, room_id);
    if (!new_player) {
        return 0;
    }
    manager->Players[manager->count] = new_player;
    manager->count++;
    return 1;
}

Player* find_player(PlayerManager *manager, struct sockaddr_in *addr) {
    if (!manager || !addr) {
        return NULL;
    }
    for (int i = 0; i < manager->count; i++) {
        Player *player = manager->Players[i];
        if (player->addr.sin_family == addr->sin_family &&
            player->addr.sin_addr.s_addr == addr->sin_addr.s_addr &&
            player->addr.sin_port == addr->sin_port) {
            return player;
        }
    }
    return NULL;
}
int remove_player(PlayerManager *manager, struct sockaddr_in *addr) {
    if (!manager || !addr) {
        return 0;
    }
    for (int i = 0; i < manager->count; i++) {
        Player *player = manager->Players[i];
        if (player->addr.sin_family == addr->sin_family &&
            player->addr.sin_addr.s_addr == addr->sin_addr.s_addr &&
            player->addr.sin_port == addr->sin_port) {
            manager->Players[i] = manager->Players[manager->count - 1];
            manager->count--;
            return 1;
        }
    }
    return 0;
}
Player* find_player_by_name(PlayerManager *manager, const char *name) {
    if (!manager || !name) {
        return NULL;
    }
    for (int i = 0; i < manager->count; i++) {
        Player *player = manager->Players[i];
        if (strcmp(player->name, name) == 0) {
            return player;
        }
    }
    return NULL;
}
void free_player_manager(PlayerManager *manager) {
    if (!manager) return;
    for (int i = 0; i < manager->count; i++) {
        free(manager->Players[i]);
    }
    pthread_mutex_destroy(&manager->mutex);
    free(manager);
}


int update_player_address(Player *player, struct sockaddr_in *new_addr) {
    if (!player || !new_addr) {
        return 0;
    }
    memcpy(&player->addr, new_addr, sizeof(struct sockaddr_in));
    return 1;
}
