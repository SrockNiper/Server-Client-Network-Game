//
// Created by mj on 11/11/25.
//

#ifndef UPS_PLAYER_H
#define UPS_PLAYER_H

#include <netinet/in.h>
#include <stdbool.h>
#include <time.h>
#include <pthread.h>

#define MAX_PLAYER_NAME 32
#define MAX_PLAYERS 40
#define PLAYER_BUFFER_SIZE 256
#define INACTIVITY_PING_TIMEOUT 60
#define INACTIVITY_DISCONNECT_TIMEOUT 600

typedef struct Player {
    char name[MAX_PLAYER_NAME];
    struct sockaddr_in addr;
    int room_id;
    bool is_active;
    bool long_inactive;
    char input_buffer[PLAYER_BUFFER_SIZE];
    int buffer_usage;
    time_t last_active_time;
    bool is_disconnected;
    bool is_ready;
    time_t last_vote_broadcast_time;

    int total_score;
    int round_score;
    int current_dice[6];
    int active_dice_count;
    bool is_on_turn;

    int pending_msg_id;
    int pending_cmd;
    char pending_msg_buffer[256];
    bool waiting_for_ack;
    struct timespec last_send_time;
} Player;

typedef struct PlayerManager{
    Player *Players[MAX_PLAYERS];
    int count;
    pthread_mutex_t mutex;
} PlayerManager;

int init_player_manager(PlayerManager *manager);
struct PlayerManager *playerManager_alloc();
int add_player(PlayerManager *manager, const char *name, struct sockaddr_in *addr, int room_id);
Player* find_player(PlayerManager *manager, struct sockaddr_in *addr);
int remove_player(PlayerManager *manager, struct sockaddr_in *addr);
int init_player(Player *player, const char *name, struct sockaddr_in *addr, int room_id);
struct Player* player_alloc(const char *name, struct sockaddr_in *addr, int room_id);
Player* find_player_by_name(PlayerManager *manager, const char *name);
int update_player_address(Player *player, struct sockaddr_in *new_addr);
void free_player_manager(PlayerManager *manager);
#endif //UPS_PLAYER_H
