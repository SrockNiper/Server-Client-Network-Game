#ifndef UPS_GAME_LOGIC_H
#define UPS_GAME_LOGIC_H

#include "player.h"
#include "room.h"
#include "protocol.h"
#include <netinet/in.h>

int calculate_farkle_score(int dice[], int count);
bool validate_selection_purity(int *dice, int count, const char *mask);
bool check_farkle(int dice[], int count);
void roll_player_dice(Player *player, int server_sock);
void send_dice_state(Player *player, int server_sock, PlayerManager *pm);
void next_turn(Room *room, PlayerManager *pm, int server_sock);
int handle_game_move(PlayerManager *player_manager, RoomManager *room_manager,
                     Message *msg, struct sockaddr_in *sender, int server_sock);
int handle_vote(PlayerManager *player_manager, RoomManager *room_manager,
                Message *msg, struct sockaddr_in *sender, int server_sock);

#endif //UPS_GAME_LOGIC_H

