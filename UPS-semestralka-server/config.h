//
// Created by mj on 11/9/25.
//
#ifndef UPS_CONFIG_H
#define UPS_CONFIG_H

#define MAX_ROOMS 10
#define NETWORK_DELAY_US 5000
#define NETWORK_DELAY_LONG_US 10000

#define PLAYER_TIMEOUT_SECONDS 120
#define LAG_WARNING_SECONDS 15
#define PING_INTERVAL_SECONDS 10

typedef struct {
    int max_players;
    int winning_score;
} RoomConfig;

typedef struct {
    char server_address[64];
    int server_port;
    int max_lobby_players;
    int max_rooms;
    int room_count;
    RoomConfig rooms[MAX_ROOMS];
} ServerConfig;

int load_config(const char *filename, ServerConfig *config);

#endif
