//
// Created by mj on 11/15/25.
//

#include "commands.h"
#include "reliability.h"
#include "game_logic.h"
#include "config.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <ctype.h>
#include <unistd.h>

void send_room_player_list(int sock, struct sockaddr_in *client_addr, Room *room);

Player* find_player_by_nickname_in_message(PlayerManager *pm, const char *data, int length) {
    if (!pm || !data || length < 6) return NULL;

    Message msg;
    if (!parse_message(data, length, &msg)) {
        return NULL;
    }

    if (msg.command != CMD_JOIN_LOBBY || !msg.content) {
        free_message(&msg);
        return NULL;
    }

    Player *found = NULL;
    for (int i = 0; i < pm->count; i++) {
        Player *p = pm->Players[i];
        if (p->is_disconnected && strcmp(p->name, msg.content) == 0) {
            found = p;
            break;
        }
    }

    free_message(&msg);
    return found;
}

int handle_join_lobby(PlayerManager *player_manager, RoomManager *room_manager,
                      Message *msg, struct sockaddr_in *client_addr, int server_sock) {
    if (!msg->content || msg->content_length == 0) {
        send_game_message(server_sock, client_addr, CMD_JOIN_LOBBY, "ERR:Name");
        return 0;
    }

    Player *existing_player = find_player_by_name(player_manager, msg->content);
    if (existing_player) {
        if (existing_player->addr.sin_addr.s_addr == client_addr->sin_addr.s_addr) {
            if (existing_player->addr.sin_port == client_addr->sin_port) {
                existing_player->is_disconnected = false;
                existing_player->long_inactive = false;
                existing_player->is_active = true;
                existing_player->last_active_time = time(NULL);

                if (existing_player->room_id != LOBBY_ID && existing_player->room_id < room_manager->count) {
                    Room *room = &room_manager->rooms[existing_player->room_id];

                    if (room->max_players > 0) {
                        char status_msg[64];
                        snprintf(status_msg, sizeof(status_msg), "JOINED %s", existing_player->name);

                        for (int i = 0; i < player_manager->count; i++) {
                            Player *p = player_manager->Players[i];
                            if (p->room_id == room->id && !p->is_disconnected && p != existing_player) {
                                send_game_message(server_sock, &p->addr, CMD_JOIN_ROOM, status_msg);
                                usleep(NETWORK_DELAY_US);
                            }
                        }
                    }
                }
            } else {
                update_player_address(existing_player, client_addr);
                existing_player->is_disconnected = false;
                existing_player->long_inactive = false;
                existing_player->is_active = true;
                existing_player->last_active_time = time(NULL);

                if (existing_player->room_id != LOBBY_ID && existing_player->room_id < room_manager->count) {
                    Room *room = &room_manager->rooms[existing_player->room_id];

                    if (room->max_players > 0) {
                        char status_msg[64];
                        snprintf(status_msg, sizeof(status_msg), "JOINED %s", existing_player->name);

                        for (int i = 0; i < player_manager->count; i++) {
                            Player *p = player_manager->Players[i];
                            if (p->room_id == room->id && !p->is_disconnected && p != existing_player) {
                                send_game_message(server_sock, &p->addr, CMD_JOIN_ROOM, status_msg);
                                usleep(NETWORK_DELAY_US);
                            }
                        }

                        printf("[Lobby] Broadcasted reconnection status (port change) for %s to room %d\n",
                               existing_player->name, room->id);
                    }
                }
            }

            if (existing_player->room_id == LOBBY_ID) {
                send_room_list(server_sock, client_addr, room_manager);
            }
            else {
                if (existing_player->room_id >= room_manager->count) {
                    existing_player->room_id = LOBBY_ID;
                    existing_player->is_ready = false;
                    existing_player->is_on_turn = false;
                    existing_player->total_score = 0;
                    existing_player->round_score = 0;

                    send_game_message(server_sock, client_addr, CMD_JOIN_LOBBY, "OK");
                    usleep(NETWORK_DELAY_LONG_US);
                    send_room_list(server_sock, client_addr, room_manager);
                    return 1;
                }

                Room *room = &room_manager->rooms[existing_player->room_id];

                if (room->max_players == 0) {
                    existing_player->room_id = LOBBY_ID;
                    existing_player->is_ready = false;
                    existing_player->is_on_turn = false;
                    existing_player->total_score = 0;
                    existing_player->round_score = 0;

                    send_game_message(server_sock, client_addr, CMD_JOIN_LOBBY, "OK");
                    usleep(NETWORK_DELAY_LONG_US);
                    send_room_list(server_sock, client_addr, room_manager);
                    return 1;
                }

                if (!room->is_game_active && existing_player->total_score > 0) {
                    existing_player->room_id = LOBBY_ID;
                    existing_player->is_ready = false;
                    existing_player->is_on_turn = false;
                    existing_player->total_score = 0;
                    existing_player->round_score = 0;

                    send_game_message(server_sock, client_addr, CMD_JOIN_LOBBY, "OK");
                    usleep(NETWORK_DELAY_LONG_US);
                    send_room_list(server_sock, client_addr, room_manager);
                    return 1;
                }

                char status_msg[64];
                snprintf(status_msg, sizeof(status_msg), "JOINED %s", existing_player->name);

                for (int i = 0; i < player_manager->count; i++) {
                    Player *p = player_manager->Players[i];
                    if (p->room_id == room->id && !p->is_disconnected && p != existing_player) {
                        send_game_message(server_sock, &p->addr, CMD_JOIN_ROOM, status_msg);
                        usleep(NETWORK_DELAY_US);
                    }
                }

                send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "OK");
                usleep(NETWORK_DELAY_LONG_US);

                char room_id_str[16];
                snprintf(room_id_str, sizeof(room_id_str), "%d", existing_player->room_id);
                send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, room_id_str);
                usleep(NETWORK_DELAY_LONG_US);

                send_room_player_list(server_sock, client_addr, room);
                usleep(NETWORK_DELAY_LONG_US);

                if (room->is_game_active) {
                    pthread_mutex_lock(&player_manager->mutex);

                    Player *active_turn_player = NULL;
                    for (int i = 0; i < player_manager->count; i++) {
                        Player *p = player_manager->Players[i];
                        if (p->room_id == room->id && p->is_on_turn) {
                            active_turn_player = p;
                            break;
                        }
                    }

                    if (active_turn_player) {
                        char turn_msg[64];
                        snprintf(turn_msg, sizeof(turn_msg), "TURN %s", active_turn_player->name);
                        send_game_message(server_sock, client_addr, CMD_VOTE, turn_msg);
                        usleep(NETWORK_DELAY_LONG_US);
                    }

                    char dice_msg[16];
                    snprintf(dice_msg, sizeof(dice_msg), "%d%d%d%d%d%d%d",
                             existing_player->active_dice_count,
                             existing_player->current_dice[0],
                             existing_player->current_dice[1],
                             existing_player->current_dice[2],
                             existing_player->current_dice[3],
                             existing_player->current_dice[4],
                             existing_player->current_dice[5]);
                    send_game_message(server_sock, client_addr, CMD_GAME_PASS, dice_msg);
                    usleep(NETWORK_DELAY_LONG_US);

                    if (active_turn_player) {
                        char round_msg[64];
                        snprintf(round_msg, sizeof(round_msg), "ROUND %s %d",
                                 active_turn_player->name, active_turn_player->round_score);
                        send_game_message(server_sock, client_addr, CMD_VOTE, round_msg);
                        usleep(NETWORK_DELAY_LONG_US);
                    }

                    for (int i = 0; i < player_manager->count; i++) {
                        Player *p = player_manager->Players[i];
                        if (p->room_id == room->id && !p->is_disconnected) {
                            char score_msg[64];
                            snprintf(score_msg, sizeof(score_msg), "SCORE %s %d",
                                     p->name, p->total_score);
                            send_game_message(server_sock, client_addr, CMD_VOTE, score_msg);
                            usleep(NETWORK_DELAY_LONG_US);
                        }
                    }

                    pthread_mutex_unlock(&player_manager->mutex);
                }
            }

            return 1;
        }

        send_game_message(server_sock, client_addr, CMD_JOIN_LOBBY, "ERR:Exist");
        return 0;
    }

    Room *lobby = &room_manager->rooms[LOBBY_ID];
    if (lobby->player_count >= lobby->max_players) {
        send_game_message(server_sock, client_addr, CMD_JOIN_LOBBY, "ERR:Full");
        return 0;
    }

    if (!add_player(player_manager, msg->content, client_addr, LOBBY_ID)) {
        send_game_message(server_sock, client_addr, CMD_JOIN_LOBBY, "ERR:Alloc");
        return 0;
    }

    lobby->player_count++;

    printf("[Lobby] %s added (%d/%d) from %s:%d\n",
           msg->content, lobby->player_count, lobby->max_players,
           inet_ntoa(client_addr->sin_addr),
           ntohs(client_addr->sin_port));

    send_room_list(server_sock, client_addr, room_manager);

    return 1;
}

void send_room_player_list(int sock, struct sockaddr_in *client_addr, Room *room) {
    if (!room || !client_addr || !room->player_manager) return;

    int active_count = 0;
    for (int i = 0; i < room->player_manager->count; i++) {
        Player *p = room->player_manager->Players[i];
        if (p->room_id == room->id && !p->is_disconnected) {
            active_count++;
        }
    }

    char count_str[16];
    snprintf(count_str, sizeof(count_str), "%d", active_count);
    send_game_message(sock, client_addr, 2, count_str);
    usleep(NETWORK_DELAY_LONG_US);

    for (int i = 0; i < room->player_manager->count; i++) {
        Player *p = room->player_manager->Players[i];
        if (p->room_id == room->id && !p->is_disconnected) {
            char player_info[128];
            snprintf(player_info, sizeof(player_info), "%s;%d;%s",
                     p->name,
                     0,
                     p->is_active ? "Active" : "Waiting");

            send_game_message(sock, client_addr, 4, player_info);
            usleep(NETWORK_DELAY_LONG_US);
        }
    }
}

int handle_join_room(PlayerManager *player_manager, RoomManager *room_manager,Message *msg, struct sockaddr_in *client_addr, int server_sock) {
    if (!msg->content) {
        send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:Invalid");
        return 0;
    }

    Player *player = find_player(player_manager, client_addr);
    if (!player) {
        send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:NoPlayer");
        return 0;
    }

    if (msg->content[0] == 'C') {
        int capacity, score;
        if (sscanf(msg->content, "C;%d;%d", &capacity, &score) != 2) {
            send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:Invalid");
            return 0;
        }

        if (capacity < 2 || capacity > 6) {
            send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:Capacity");
            return 0;
        }

        if (score <= 0) {
            send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:Score");
            return 0;
        }

        int max_active_id = 0;
        for (int i = 1; i < room_manager->count; i++) {
            Room *r = &room_manager->rooms[i];
            if (r->max_players > 0 && r->id > max_active_id) {
                max_active_id = r->id;
            }
        }

        int target_id = max_active_id + 1;
        int target_index = -1;
        bool need_expansion = false;

        if (target_id < room_manager->count) {
            target_index = target_id;
        }
        else if (target_id == room_manager->count && room_manager->count < MAX_ROOMS) {
            target_index = room_manager->count;
            need_expansion = true;
        }
        else {
            for (int i = 1; i < room_manager->count; i++) {
                Room *r = &room_manager->rooms[i];
                if (r->max_players == 0) {
                    target_index = i;
                    target_id = i;
                    break;
                }
            }
        }

        if (target_index == -1) {
            send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:Full");
            return 0;
        }

        if (need_expansion) {
            room_manager->count++;
        }

        Room *new_room = &room_manager->rooms[target_index];

        if (!init_dynamic_room(new_room, target_id, capacity, score, server_sock, player_manager, room_manager)) {
            if (need_expansion) {
                room_manager->count--;
            }
            send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:InitFailed");
            return 0;
        }

        room_manager->rooms[player->room_id].player_count--;
        player->room_id = target_index;
        new_room->player_count++;

        printf("[Room %d] %s created room (capacity: %d, score: %d)\n",
               target_id, player->name, capacity, score);

        send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "OK");

        broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);

        send_room_player_list(server_sock, client_addr, new_room);
        return 1;
    }

    if (!isdigit(msg->content[0])) {
        send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:Invalid");
        return 0;
    }

    int room_id = msg->content[0] - '0';

    if (room_id < 1 || room_id >= room_manager->count) {
        send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:NoRoom");
        return 0;
    }

    Room *target_room = &room_manager->rooms[room_id];

    if (target_room->is_game_active) {
        send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:GameRunning");
        return 0;
    }

    if (target_room->player_count >= target_room->max_players) {
        send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:Full");
        return 0;
    }
    if (player->room_id == room_id) {
        send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "ERR:Exist");
        return 0;
    }

    room_manager->rooms[player->room_id].player_count--;
    player->room_id = room_id;
    target_room->player_count++;

    printf("[Room %d] %s joined (%d/%d)\n",
           room_id, player->name, target_room->player_count, target_room->max_players);

    send_game_message(server_sock, client_addr, CMD_JOIN_ROOM, "OK");

    char notify[64];
    snprintf(notify, sizeof(notify), "JOINED %s", player->name);
    broadcast_to_room(server_sock, room_manager, player_manager, room_id, CMD_JOIN_ROOM, notify, client_addr);

    broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);

    send_room_player_list(server_sock, client_addr, target_room);
    return 1;
}

int handle_return_to_lobby(PlayerManager *player_manager, RoomManager *room_manager,Message *msg, struct sockaddr_in *client_addr, int server_sock) {
    Player *player = find_player(player_manager, client_addr);
    if (!player) {
        send_game_message(server_sock, client_addr, CMD_RETURN_TO_LOBBY, "ERR:NoPlayer");
        return 0;
    }

    if (player->room_id == LOBBY_ID) {
        send_game_message(server_sock, client_addr, CMD_RETURN_TO_LOBBY, "ERR:Already");
        return 0;
    }

    int old_room_id = player->room_id;
    Room *old_room = &room_manager->rooms[old_room_id];

    Room *lobby = &room_manager->rooms[LOBBY_ID];
    if (lobby->player_count >= lobby->max_players) {
        send_game_message(server_sock, client_addr, CMD_RETURN_TO_LOBBY, "ERR:Full");
        return 0;
    }

    old_room->player_count--;
    lobby->player_count++;

    player->room_id = LOBBY_ID;
    player->is_ready = false;
    player->round_score = 0;
    player->total_score = 0;
    player->is_on_turn = false;
    player->active_dice_count = 6;

    for (int i = 0; i < 6; i++) {
        player->current_dice[i] = 0;
    }

    player->waiting_for_ack = false;
    player->pending_msg_id = 0;
    memset(player->pending_msg_buffer, 0, sizeof(player->pending_msg_buffer));

    printf("[Lobby] %s returned to lobby (%d/%d)\n",
           player->name, lobby->player_count, lobby->max_players);

    if (old_room->player_count == 0 && old_room->id != LOBBY_ID) {
        old_room->max_players = 0;
        old_room->winning_score = 0;
        old_room->is_game_active = false;
        old_room->is_vote_active = false;
    }

    send_game_message(server_sock, client_addr, CMD_RETURN_TO_LOBBY, "OK");
    usleep(NETWORK_DELAY_LONG_US);

    send_room_list(server_sock, client_addr, room_manager);

    char notify[64];
    snprintf(notify, sizeof(notify), "LEFT %s", player->name);
    broadcast_to_room(server_sock, room_manager, player_manager, old_room_id, CMD_RETURN_TO_LOBBY, notify, client_addr);

    broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);

    return 1;
}

int handle_disconnect(PlayerManager *player_manager, RoomManager *room_manager,Message *msg, struct sockaddr_in *client_addr, int server_sock) {
    Player *player = find_player(player_manager, client_addr);
    if (!player) {
        send_game_message(server_sock, client_addr, CMD_DISCONNECT, "ERR:NoPlayer");
        return 0;
    }

    int room_id = player->room_id;
    Room *room = &room_manager->rooms[room_id];

    room->player_count--;

    printf("[Server] %s disconnected\n", player->name);

    char disconnect_status_msg[128];
    snprintf(disconnect_status_msg, sizeof(disconnect_status_msg),
             "PLAYER_DISCONNECTED %s", player->name);

    for (int i = 0; i < player_manager->count; i++) {
        Player *other = player_manager->Players[i];
        if (other->room_id == room_id && !other->is_disconnected && other != player) {
            send_game_message(server_sock, &other->addr, CMD_VOTE, disconnect_status_msg);
            usleep(NETWORK_DELAY_US);
        }
    }

    if (room->is_game_active && room->player_count < 2) {
        Player *survivor = NULL;
        for (int i = 0; i < player_manager->count; i++) {
            Player *p = player_manager->Players[i];
            if (p->room_id == room_id && !p->is_disconnected && p != player) {
                survivor = p;
                break;
            }
        }

        if (survivor) {
            room->is_game_active = false;

            char win_msg[64];
            snprintf(win_msg, sizeof(win_msg), "WIN %s", survivor->name);

            printf("[Game Room %d] Game ended - winner: %s\n", room_id, survivor->name);

            send_game_message(server_sock, &survivor->addr, CMD_VOTE, win_msg);

            survivor->is_on_turn = false;
            survivor->total_score = 0;
            survivor->round_score = 0;
            survivor->active_dice_count = 6;
            survivor->is_ready = false;

            room->current_player_index = -1;
        }
    }

    remove_player(player_manager, client_addr);

    send_game_message(server_sock, client_addr, CMD_DISCONNECT, "OK");

    if (room->player_count == 0 && room->id != LOBBY_ID) {
        room->max_players = 0;
        room->winning_score = 0;
    }

    broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);

    return 1;
}

void send_room_list(int sock, struct sockaddr_in *client_addr, RoomManager *rm) {
    if (!rm || !client_addr) {
        return;
    }

    int active_room_count = 0;
    for (int i = 1; i < rm->count; i++) {
        if (rm->rooms[i].max_players > 0 && !rm->rooms[i].is_game_active) {
            active_room_count++;
        }
    }

    char count_str[16];
    snprintf(count_str, sizeof(count_str), "%d", active_room_count);

    send_game_message(sock, client_addr, 1, count_str);
    usleep(NETWORK_DELAY_LONG_US);

    for (int i = 1; i < rm->count; i++) {
        Room *room = &rm->rooms[i];

        if (room->max_players == 0) {
            continue;
        }

        if (room->is_game_active) {
            continue;
        }

        char room_info[64];
        snprintf(room_info, sizeof(room_info), "%d;%d;%d;%d",
                 room->id, room->player_count, room->max_players, room->winning_score);

        send_game_message(sock, client_addr, 1, room_info);
        usleep(NETWORK_DELAY_LONG_US);
    }
}

void broadcast_room_list_to_lobby(int server_sock, PlayerManager *player_manager, RoomManager *room_manager) {
    if (!player_manager || !room_manager) {
        return;
    }

    pthread_mutex_lock(&player_manager->mutex);

    for (int i = 0; i < player_manager->count; i++) {
        Player *p = player_manager->Players[i];
        if (p && p->room_id == LOBBY_ID && !p->is_disconnected) {
            send_room_list(server_sock, &p->addr, room_manager);
            usleep(NETWORK_DELAY_US);
        }
    }

    pthread_mutex_unlock(&player_manager->mutex);
}

