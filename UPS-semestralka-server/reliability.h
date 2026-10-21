
#ifndef UPS_RELIABILITY_H
#define UPS_RELIABILITY_H

#include <netinet/in.h>

// Forward declarations
typedef struct Player Player;
typedef struct Room Room;
typedef struct RoomManager RoomManager;
typedef struct PlayerManager PlayerManager;

#include "player.h"
#include "room.h"
#include "protocol.h"

int send_reliable_message(int sock, Player *p, int cmd, const char *content);
int handle_ack(PlayerManager *player_manager, Message *msg, struct sockaddr_in *client_addr);
void check_retransmissions(Room *room);
void broadcast_to_room(int server_sock, RoomManager *rm, PlayerManager *pm, int room_id, int cmd, const char *message, struct sockaddr_in *sender_addr);
void broadcast_reliable(int server_sock, RoomManager *rm, PlayerManager *pm, int room_id, int cmd, const char *message, struct sockaddr_in *sender_addr);

#endif //UPS_RELIABILITY_H

