//
// Created by mj on 11/15/25.
//

#ifndef UPS_COMMANDS_H
#define UPS_COMMANDS_H

#include "player.h"
#include "room.h"
#include "protocol.h"
#include <netinet/in.h>

#define CMD_JOIN_LOBBY 1
#define CMD_JOIN_ROOM 2
#define CMD_RETURN_TO_LOBBY 6
#define CMD_DISCONNECT 0
#define CMD_VOTE 7
#define CMD_ACK 8
#define CMD_PING 9

int handle_join_lobby(PlayerManager *player_manager, RoomManager *room_manager,Message *msg, struct sockaddr_in *client_addr, int server_sock);
int handle_join_room(PlayerManager *player_manager, RoomManager *room_manager,Message *msg, struct sockaddr_in *client_addr, int server_sock);
int handle_return_to_lobby(PlayerManager *player_manager, RoomManager *room_manager,Message *msg, struct sockaddr_in *client_addr, int server_sock);
int handle_disconnect(PlayerManager *player_manager, RoomManager *room_manager,Message *msg, struct sockaddr_in *client_addr, int server_sock);

void send_room_list(int sock, struct sockaddr_in *client_addr, RoomManager *rm);
void send_room_player_list(int sock, struct sockaddr_in *client_addr, Room *room);
void broadcast_room_list_to_lobby(int server_sock, PlayerManager *player_manager, RoomManager *room_manager);

Player* find_player_by_nickname_in_message(PlayerManager *pm, const char *data, int length);


#endif //UPS_COMMANDS_H
