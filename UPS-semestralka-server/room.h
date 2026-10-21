//
// Created by mj on 11/12/25.
//

#ifndef UPS_ROOM_H
#define UPS_ROOM_H

#include "player.h"
#include "config.h"
#include "message_queue.h"
#include <stdbool.h>
#include <netinet/in.h>
#include <pthread.h>

#define LOBBY_ID 0

typedef struct PlayerManager PlayerManager;
typedef struct RoomManager RoomManager;

typedef struct Room{
    int id;
    int max_players;
    int winning_score;
    int player_count;
    bool is_game_active;
    int current_player_index;

    bool is_vote_active;
    char vote_target_name[32];
    int vote_kick_count;
    int vote_wait_count;

    MessageQueue queue;
    pthread_t thread_id;
    int running;

    int server_sock;
    PlayerManager *player_manager;
    RoomManager *room_manager;

} Room;

typedef struct RoomManager{
    Room *rooms;
    int count;
} RoomManager;

int init_room_manager(RoomManager *manager, ServerConfig *config);
void free_room_manager(RoomManager *manager);
struct RoomManager* roomManager_alloc(ServerConfig *config);

int init_dynamic_room(Room *room, int room_id, int max_players, int winning_score, int server_sock, PlayerManager *pm, RoomManager *rm);

void* room_thread_func(void *arg);
int start_room_threads(RoomManager *manager, int server_sock, PlayerManager *player_manager);
void stop_room_threads(RoomManager *manager);

#endif
