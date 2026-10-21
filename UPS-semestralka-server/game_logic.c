//
// Created by mj on 12/30/25.
//

#include "game_logic.h"
#include "reliability.h"
#include "protocol.h"
#include "player.h"
#include "commands.h"
#include "config.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <pthread.h>

#define CMD_GAME_PASS 3
#define CMD_GAME_REROLL 4
#define CMD_GAME_WIN 5

bool validate_selection_purity(int *dice, int count, const char *mask) {
    if (count == 0) return true;

    int freq[7] = {0};

    for (int i = 0; i < count; i++) {
        if (dice[i] >= 1 && dice[i] <= 6) {
            freq[dice[i]]++;
        }
    }

    if (count == 6 && freq[1] == 1 && freq[2] == 1 && freq[3] == 1 &&
        freq[4] == 1 && freq[5] == 1 && freq[6] == 1) {
        return true;
    }

    for (int value = 1; value <= 6; value++) {
        if (freq[value] == 0) continue;

        if (freq[value] >= 3) {
            continue;
        }

        if (value == 1 || value == 5) {
            continue;
        }

        return false;
    }

    return true;
}

int calculate_farkle_score(int dice[], int count) {
    if (count == 0) return 0;

    int score = 0;
    int freq[7] = {0};

    for (int i = 0; i < count; i++) {
        if (dice[i] >= 1 && dice[i] <= 6) {
            freq[dice[i]]++;
        }
    }

    if (count == 6 && freq[1] == 1 && freq[2] == 1 && freq[3] == 1 &&
        freq[4] == 1 && freq[5] == 1 && freq[6] == 1) {
        return 1500;
    }

    for (int value = 1; value <= 6; value++) {
        if (freq[value] >= 3) {
            int base_score = (value == 1) ? 1000 : (value * 100);
            int dice_count = freq[value];
            int multiplier = 1;

            if (dice_count == 3) {
                multiplier = 1;
            } else if (dice_count == 4) {
                multiplier = 2;
            } else if (dice_count == 5) {
                multiplier = 4;
            } else if (dice_count == 6) {
                multiplier = 8;
            }

            score += base_score * multiplier;
            freq[value] = 0;
        }
    }

    score += freq[1] * 100;
    score += freq[5] * 50;

    return score;
}

bool check_farkle(int dice[], int count) {
    if (count == 0) return true;

    int score = calculate_farkle_score(dice, count);
    return (score == 0);
}

void roll_player_dice(Player *player, int server_sock) {
    for (int i = 0; i < player->active_dice_count; i++) {
        player->current_dice[i] = (rand() % 6) + 1;
    }

    for (int i = player->active_dice_count; i < 6; i++) {
        player->current_dice[i] = 0;
    }

    printf("[Game Room %d] %s rolled %d dice: ", player->room_id, player->name, player->active_dice_count);
    for (int i = 0; i < player->active_dice_count; i++) {
        printf("%d ", player->current_dice[i]);
    }
    printf("\n");
}

void send_dice_state(Player *player, int server_sock, PlayerManager *pm) {
    if (!player || !pm) return;

    char dice_msg[8];
    snprintf(dice_msg, sizeof(dice_msg), "%d%d%d%d%d%d%d",
             player->active_dice_count,
             player->current_dice[0],
             player->current_dice[1],
             player->current_dice[2],
             player->current_dice[3],
             player->current_dice[4],
             player->current_dice[5]);

    for (int i = 0; i < pm->count; i++) {
        Player *p = pm->Players[i];
        if (p && p->room_id == player->room_id && !p->is_disconnected) {
            send_game_message(server_sock, &p->addr, CMD_GAME_PASS, dice_msg);
            usleep(NETWORK_DELAY_US);
        }
    }
}

void next_turn(Room *room, PlayerManager *pm, int server_sock) {
    pthread_mutex_lock(&pm->mutex);

    for (int i = 0; i < pm->count; i++) {
        if (pm->Players[i]->is_on_turn) {
            pm->Players[i]->is_on_turn = false;
            break;
        }
    }

    Player *active_players[MAX_PLAYERS];
    int active_count = 0;

    for (int i = 0; i < pm->count; i++) {
        Player *p = pm->Players[i];
        if (p->room_id == room->id && !p->is_disconnected && p->is_active) {
            active_players[active_count++] = p;
        }
    }

    if (active_count == 0) {
        pthread_mutex_unlock(&pm->mutex);
        return;
    }

    room->current_player_index = (room->current_player_index + 1) % active_count;
    Player *next_player = active_players[room->current_player_index];

    next_player->is_on_turn = true;
    next_player->round_score = 0;
    next_player->active_dice_count = 6;

    roll_player_dice(next_player, server_sock);

    if (check_farkle(next_player->current_dice, next_player->active_dice_count)) {
        printf("[Game Room %d] FARKLE! %s has no scoring dice\n", room->id, next_player->name);

        char dice_string[8];
        snprintf(dice_string, sizeof(dice_string), "%d%d%d%d%d%d%d",
                 next_player->active_dice_count,
                 next_player->current_dice[0],
                 next_player->current_dice[1],
                 next_player->current_dice[2],
                 next_player->current_dice[3],
                 next_player->current_dice[4],
                 next_player->current_dice[5]);

        char farkle_msg[128];
        snprintf(farkle_msg, sizeof(farkle_msg), "FARKLE %s;%s", next_player->name, dice_string);

        for (int i = 0; i < active_count; i++) {
            send_reliable_message(server_sock, active_players[i], CMD_VOTE, farkle_msg);
            usleep(NETWORK_DELAY_US);
        }

        next_player->round_score = 0;
        next_player->is_on_turn = false;

        pthread_mutex_unlock(&pm->mutex);

        next_turn(room, pm, server_sock);
        return;
    }

    printf("[Game Room %d] Turn passed to %s (Total Score: %d)\n",
           room->id, next_player->name, next_player->total_score);

    char dice_string[8];
    snprintf(dice_string, sizeof(dice_string), "%d%d%d%d%d%d%d",
             next_player->active_dice_count,
             next_player->current_dice[0],
             next_player->current_dice[1],
             next_player->current_dice[2],
             next_player->current_dice[3],
             next_player->current_dice[4],
             next_player->current_dice[5]);

    char turn_msg[128];
    snprintf(turn_msg, sizeof(turn_msg), "TURN;%s;%s", next_player->name, dice_string);

    for (int i = 0; i < active_count; i++) {
        send_reliable_message(server_sock, active_players[i], CMD_VOTE, turn_msg);
        usleep(NETWORK_DELAY_US);
    }

    pthread_mutex_unlock(&pm->mutex);
}

int handle_game_move(PlayerManager *player_manager, RoomManager *room_manager,
                     Message *msg, struct sockaddr_in *sender, int server_sock) {
    if (!player_manager || !room_manager || !msg || !sender) {
        return 0;
    }

    pthread_mutex_lock(&player_manager->mutex);

    Player *player = find_player(player_manager, sender);
    if (!player) {
        pthread_mutex_unlock(&player_manager->mutex);
        send_game_message(server_sock, sender, msg->command, "ERR:NotFound");
        return 0;
    }

    Room *room = &room_manager->rooms[player->room_id];

    if (!room->is_game_active) {
        pthread_mutex_unlock(&player_manager->mutex);
        send_game_message(server_sock, sender, msg->command, "ERR:NoGame");
        return 0;
    }

    if (!player->is_on_turn) {
        pthread_mutex_unlock(&player_manager->mutex);
        send_game_message(server_sock, sender, msg->command, "ERR:NotYourTurn");
        return 0;
    }

    if (msg->content_length != 6) {
        pthread_mutex_unlock(&player_manager->mutex);
        send_game_message(server_sock, sender, msg->command, "ERR:InvalidFormat");
        return 0;
    }

    int current_freq[7] = {0};
    for (int i = 0; i < 6; i++) {
        if (player->current_dice[i] >= 1 && player->current_dice[i] <= 6) {
            current_freq[player->current_dice[i]]++;
        }
    }

    int selected_dice[6];
    int selected_count = 0;
    int selected_freq[7] = {0};

    for (int i = 0; i < 6; i++) {
        char c = msg->content[i];
        if (c >= '1' && c <= '6') {
            int value = c - '0';
            selected_dice[selected_count++] = value;
            selected_freq[value]++;
        } else if (c != '0') {
            pthread_mutex_unlock(&player_manager->mutex);
            send_game_message(server_sock, sender, msg->command, "ERR:InvalidDice");
            return 0;
        }
    }

        for (int i = 1; i <= 6; i++) {
        if (selected_freq[i] > current_freq[i]) {
            printf("[Game Room %d] CHEAT DETECTED: %s tried to select %d dice of value %d, but only has %d\n",
                   room->id, player->name, selected_freq[i], i, current_freq[i]);
            pthread_mutex_unlock(&player_manager->mutex);
            send_game_message(server_sock, sender, msg->command, "ERR:Cheating");
            return 0;
        }
    }

    if (!validate_selection_purity(selected_dice, selected_count, msg->content)) {
        printf("[Game Room %d] Invalid selection: %s selected dice that don't all score\n",
               room->id, player->name);
        pthread_mutex_unlock(&player_manager->mutex);
        send_game_message(server_sock, sender, msg->command, "ERR:InvalidDice");
        return 0;
    }

    int move_score = calculate_farkle_score(selected_dice, selected_count);

    printf("[Game Room %d] %s selected %d dice, score: %d\n",
           room->id, player->name, selected_count, move_score);

    if (msg->command == CMD_GAME_REROLL) {
        if (move_score == 0 && selected_count > 0) {
            pthread_mutex_unlock(&player_manager->mutex);
            send_game_message(server_sock, sender, CMD_GAME_REROLL, "ERR:NoScore");
            return 0;
        }

        char ack_msg[64];
        snprintf(ack_msg, sizeof(ack_msg), "ACK_REROLL %d", move_score);
        send_game_message(server_sock, sender, CMD_ACK, ack_msg);

        player->round_score += move_score;
        player->active_dice_count -= selected_count;

        if (player->active_dice_count == 0) {
            player->active_dice_count = 6;
            printf("[Game Room %d] HOT DICE! %s gets all 6 dice back\n", room->id, player->name);
        }

        roll_player_dice(player, server_sock);

        if (check_farkle(player->current_dice, player->active_dice_count)) {
            printf("[Game Room %d] FARKLE! %s rolled no scoring dice\n", room->id, player->name);
            send_dice_state(player, server_sock, player_manager);
            usleep(NETWORK_DELAY_LONG_US);

            char farkle_msg[64];
            snprintf(farkle_msg, sizeof(farkle_msg), "FARKLE %s", player->name);

            for (int i = 0; i < player_manager->count; i++) {
                Player *p = player_manager->Players[i];
                if (p->room_id == room->id && !p->is_disconnected) {
                    send_reliable_message(server_sock, p, CMD_VOTE, farkle_msg);
                    usleep(NETWORK_DELAY_US);
                }
            }

            player->round_score = 0;
            player->is_on_turn = false;

            pthread_mutex_unlock(&player_manager->mutex);

            next_turn(room, player_manager, server_sock);
            return 1;
        }

        send_dice_state(player, server_sock, player_manager);

        char score_msg[64];
        snprintf(score_msg, sizeof(score_msg), "ROUND %s %d",
                 player->name, player->round_score);

        for (int i = 0; i < player_manager->count; i++) {
            Player *p = player_manager->Players[i];
            if (p->room_id == room->id && !p->is_disconnected) {
                send_reliable_message(server_sock, p, CMD_VOTE, score_msg);
                usleep(NETWORK_DELAY_US);
            }
        }

        pthread_mutex_unlock(&player_manager->mutex);
        return 1;
    }
    else if (msg->command == CMD_GAME_PASS) {
        int temp_round_score = player->round_score + move_score;
        int new_total_score = player->total_score + temp_round_score;

        if (new_total_score >= room->winning_score) {
            printf("[Game Room %d] %s reached winning score via PASS! Total: %d (Needed: %d)\n",
                   room->id, player->name, new_total_score, room->winning_score);

            char ack_msg[64];
            snprintf(ack_msg, sizeof(ack_msg), "ACK_WIN %d", new_total_score);
            send_game_message(server_sock, sender, CMD_ACK, ack_msg);

            player->round_score += move_score;
            player->total_score += player->round_score;

            char final_score_msg[64];
            snprintf(final_score_msg, sizeof(final_score_msg), "SCORE %s %d",
                     player->name, player->total_score);

            for (int i = 0; i < player_manager->count; i++) {
                Player *p = player_manager->Players[i];
                if (p->room_id == room->id && !p->is_disconnected) {
                    send_game_message(server_sock, &p->addr, CMD_VOTE, final_score_msg);
                    usleep(NETWORK_DELAY_US);
                }
            }

            usleep(NETWORK_DELAY_LONG_US);

            room->is_game_active = false;

            char win_msg[64];
            snprintf(win_msg, sizeof(win_msg), "WIN %s", player->name);

            printf("[Game Room %d] GAME OVER! Winner: %s with %d points\n",
                   room->id, player->name, player->total_score);

            for (int i = 0; i < player_manager->count; i++) {
                Player *p = player_manager->Players[i];
                if (p->room_id == room->id && !p->is_disconnected) {
                    send_reliable_message(server_sock, p, CMD_VOTE, win_msg);

                    p->is_on_turn = false;
                    p->total_score = 0;
                    p->round_score = 0;
                    p->active_dice_count = 6;
                    p->is_ready = false;

                    usleep(NETWORK_DELAY_US);
                }
            }

            room->current_player_index = -1;

            pthread_mutex_unlock(&player_manager->mutex);

            broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);

            return 1;
        }

        char ack_msg[64];
        snprintf(ack_msg, sizeof(ack_msg), "ACK_PASS %d", new_total_score);
        send_game_message(server_sock, sender, CMD_ACK, ack_msg);

        player->round_score += move_score;
        player->total_score += player->round_score;

        printf("[Game Room %d] %s banks %d points (Total: %d)\n",
               room->id, player->name, player->round_score, player->total_score);

        char score_msg[64];
        snprintf(score_msg, sizeof(score_msg), "SCORE %s %d",
                 player->name, player->total_score);

        for (int i = 0; i < player_manager->count; i++) {
            Player *p = player_manager->Players[i];
            if (p->room_id == room->id && !p->is_disconnected) {
                send_reliable_message(server_sock, p, CMD_VOTE, score_msg);
                usleep(NETWORK_DELAY_US);
            }
        }

        pthread_mutex_unlock(&player_manager->mutex);

        next_turn(room, player_manager, server_sock);

        return 1;
    }
    else if (msg->command == CMD_GAME_WIN) {
        if (move_score == 0 && selected_count > 0) {
            pthread_mutex_unlock(&player_manager->mutex);
            send_game_message(server_sock, sender, CMD_GAME_WIN, "ERR:NoScore");
            return 0;
        }

        int potential_total = player->total_score + player->round_score + move_score;

        printf("[Game Room %d] %s claims victory! Potential Total: %d (Needed: %d)\n",
               room->id, player->name, potential_total, room->winning_score);

        if (potential_total < room->winning_score) {
            printf("[Game Room %d] WIN REJECTED: %s has %d but needs %d\n",
                   room->id, player->name, potential_total, room->winning_score);
            pthread_mutex_unlock(&player_manager->mutex);
            send_game_message(server_sock, sender, CMD_GAME_WIN, "ERR:NotEnough");
            return 0;
        }

        char ack_msg[64];
        snprintf(ack_msg, sizeof(ack_msg), "ACK_WIN %d", potential_total);
        send_game_message(server_sock, sender, CMD_ACK, ack_msg);

        player->round_score += move_score;
        player->total_score += player->round_score;

        char final_score_msg[64];
        snprintf(final_score_msg, sizeof(final_score_msg), "SCORE %s %d",
                 player->name, player->total_score);

        for (int i = 0; i < player_manager->count; i++) {
            Player *p = player_manager->Players[i];
            if (p->room_id == room->id && !p->is_disconnected) {
                send_game_message(server_sock, &p->addr, CMD_VOTE, final_score_msg);
                usleep(NETWORK_DELAY_US);
            }
        }

        usleep(NETWORK_DELAY_LONG_US);

        room->is_game_active = false;

        char win_msg[64];
        snprintf(win_msg, sizeof(win_msg), "WIN %s", player->name);

        printf("[Game Room %d] GAME OVER! Winner: %s with %d points\n",
               room->id, player->name, player->total_score);

        for (int i = 0; i < player_manager->count; i++) {
            Player *p = player_manager->Players[i];
            if (p->room_id == room->id && !p->is_disconnected) {
                send_reliable_message(server_sock, p, CMD_VOTE, win_msg);

                p->is_on_turn = false;
                p->total_score = 0;
                p->round_score = 0;
                p->active_dice_count = 6;
                p->is_ready = false;

                usleep(NETWORK_DELAY_US);
            }
        }

        room->current_player_index = -1;

        pthread_mutex_unlock(&player_manager->mutex);

        broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);

        return 1;
    }

    pthread_mutex_unlock(&player_manager->mutex);
    return 0;
}

int handle_vote(PlayerManager *player_manager, RoomManager *room_manager, Message *msg, struct sockaddr_in *sender, int server_sock) {
    if (!player_manager || !room_manager || !msg || !sender) {
        return 0;
    }

    bool pass_turn = false;

    pthread_mutex_lock(&player_manager->mutex);

    Player *player = find_player(player_manager, sender);
    if (!player) {
        pthread_mutex_unlock(&player_manager->mutex);
        send_game_message(server_sock, sender, CMD_VOTE, "ERR:NotFound");
        return 0;
    }

    const int room_id = player->room_id;
    Room *room = &room_manager->rooms[room_id];

    if (msg->content_length == 0) {
        player->is_ready = !player->is_ready;

        char broadcast_msg[128];
        snprintf(broadcast_msg, sizeof(broadcast_msg), "READY %s %d",
                 player->name, player->is_ready ? 1 : 0);

        for (int i = 0; i < player_manager->count; i++) {
            Player *other = player_manager->Players[i];
            if (other->room_id == room_id && !other->is_disconnected) {
                send_game_message(server_sock, &other->addr, CMD_VOTE, broadcast_msg);
                usleep(NETWORK_DELAY_US);
            }
        }

        int active_count = 0;
        int ready_count = 0;

        for (int i = 0; i < player_manager->count; i++) {
            Player *p = player_manager->Players[i];
            if (p->room_id == room_id && !p->is_disconnected && p->is_active) {
                active_count++;
                if (p->is_ready) {
                    ready_count++;
                }
            }
        }

        if (active_count == room->max_players && ready_count == active_count) {
            room->is_game_active = true;
            room->current_player_index = -1;

            for (int i = 0; i < player_manager->count; i++) {
                Player *other = player_manager->Players[i];
                if (other->room_id == room_id && !other->is_disconnected) {
                    send_game_message(server_sock, &other->addr, CMD_VOTE, "START");
                    usleep(NETWORK_DELAY_US);
                }
            }

            pthread_mutex_unlock(&player_manager->mutex);

            broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);

            next_turn(room, player_manager, server_sock);

            return 1;
        }

        pthread_mutex_unlock(&player_manager->mutex);
        return 1;
    }

    if (msg->content_length == 1 && (msg->content[0] == '0' || msg->content[0] == '1')) {
        if (!room->is_vote_active) {
            pthread_mutex_unlock(&player_manager->mutex);
            send_game_message(server_sock, sender, CMD_VOTE, "ERR:NoVote");
            return 0;
        }

        int vote_value = msg->content[0] - '0';

        if (vote_value == 1) {
            room->vote_kick_count++;
            printf("[Vote Room %d] %s voted to KICK %s (%d votes)\n",
                   room_id, player->name, room->vote_target_name, room->vote_kick_count);
        } else {
            room->vote_wait_count++;
            printf("[Vote Room %d] %s voted to WAIT for %s (%d votes)\n",
                   room_id, player->name, room->vote_target_name, room->vote_wait_count);
        }

        int voter_count = 0;
        for (int i = 0; i < player_manager->count; i++) {
            Player *p = player_manager->Players[i];
            if (p->room_id == room_id && !p->is_disconnected &&
                strcmp(p->name, room->vote_target_name) != 0) {
                voter_count++;
            }
        }

        const int total_votes = room->vote_kick_count + room->vote_wait_count;

        if (total_votes >= voter_count) {
            if (room->vote_kick_count > room->vote_wait_count) {
                printf("[Vote Room %d] Result: KICK %s (%d-%d)\n",
                       room_id, room->vote_target_name, room->vote_kick_count, room->vote_wait_count);

                Player *target = find_player_by_name(player_manager, room->vote_target_name);
                if (target) {
                    if (target->is_on_turn) {
                        target->is_on_turn = false;
                        pass_turn = true;
                    }

                    target->room_id = LOBBY_ID;
                    target->is_disconnected = true;
                    target->is_active = false;
                    target->is_ready = false;
                    target->total_score = 0;
                    target->round_score = 0;

                    room->player_count--;

                    send_game_message(server_sock, &target->addr, CMD_DISCONNECT, "PLAYER_LOST Voted");

                    char notify[128];
                    snprintf(notify, sizeof(notify), "PLAYER_LOST %s", target->name);
                    for (int i = 0; i < player_manager->count; i++) {
                        Player *p = player_manager->Players[i];
                        if (p != target && p->room_id == room_id && !p->is_disconnected) {
                            send_game_message(server_sock, &p->addr, CMD_VOTE, notify);
                        }
                    }

                    if (room->is_game_active && room->player_count < 2) {
                        Player *survivor = NULL;
                        for (int i = 0; i < player_manager->count; i++) {
                            Player *p = player_manager->Players[i];
                            if (p->room_id == room_id && !p->is_disconnected) {
                                survivor = p;
                                break;
                            }
                        }

                        if (survivor) {
                            room->is_game_active = false;
                            char win_msg[64];
                            snprintf(win_msg, sizeof(win_msg), "WIN %s", survivor->name);
                            send_game_message(server_sock, &survivor->addr, CMD_VOTE, win_msg);
                        }
                    }

                    pthread_mutex_unlock(&player_manager->mutex);
                    broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);
                    pthread_mutex_lock(&player_manager->mutex);
                }
            } else {
                Player *target = find_player_by_name(player_manager, room->vote_target_name);
                if (target) {
                    target->last_active_time = time(NULL);
                    target->long_inactive = false;
                }

                char notify[128];
                snprintf(notify, sizeof(notify), "VOTE_END");
                for (int i = 0; i < player_manager->count; i++) {
                    Player *p = player_manager->Players[i];
                    if (p->room_id == room_id && !p->is_disconnected) {
                        send_game_message(server_sock, &p->addr, CMD_VOTE, notify);
                    }
                }
            }

            room->is_vote_active = false;
            memset(room->vote_target_name, 0, sizeof(room->vote_target_name));
            room->vote_kick_count = 0;
            room->vote_wait_count = 0;
        }

        pthread_mutex_unlock(&player_manager->mutex);

        if (pass_turn) {
            next_turn(room, player_manager, server_sock);
        }

        return 1;
    }

    if (msg->content_length > 5 && strncmp(msg->content, "VOTE ", 5) == 0) {
        if (!room->is_vote_active) {
            pthread_mutex_unlock(&player_manager->mutex);
            send_game_message(server_sock, sender, CMD_VOTE, "ERR:NoVote");
            return 0;
        }

        const char *vote_type = msg->content + 5;

        if (strcmp(vote_type, "KICK") == 0) {
            room->vote_kick_count++;
            printf("[Vote Room %d] %s voted to KICK %s (%d votes)\n",
                   room_id, player->name, room->vote_target_name, room->vote_kick_count);
        } else if (strcmp(vote_type, "WAIT") == 0) {
            room->vote_wait_count++;
            printf("[Vote Room %d] %s voted to WAIT for %s (%d votes)\n",
                   room_id, player->name, room->vote_target_name, room->vote_wait_count);
        } else {
            pthread_mutex_unlock(&player_manager->mutex);
            send_game_message(server_sock, sender, CMD_VOTE, "ERR:Invalid");
            return 0;
        }

        int voter_count = 0;
        for (int i = 0; i < player_manager->count; i++) {
            Player *p = player_manager->Players[i];
            if (p->room_id == room_id && !p->is_disconnected &&
                strcmp(p->name, room->vote_target_name) != 0) {
                voter_count++;
            }
        }

        int total_votes = room->vote_kick_count + room->vote_wait_count;

        if (total_votes >= voter_count) {
            if (room->vote_kick_count > room->vote_wait_count) {
                Player *target = find_player_by_name(player_manager, room->vote_target_name);
                if (target) {
                    if (target->is_on_turn) {
                        target->is_on_turn = false;
                        pass_turn = true;
                    }

                    target->room_id = LOBBY_ID;
                    target->is_disconnected = true;
                    target->is_active = false;
                    target->is_ready = false;
                    target->total_score = 0;
                    target->round_score = 0;

                    room->player_count--;

                    send_game_message(server_sock, &target->addr, CMD_DISCONNECT, "PLAYER_LOST Voted");

                    char notify[128];
                    snprintf(notify, sizeof(notify), "PLAYER_LOST %s", target->name);
                    for (int i = 0; i < player_manager->count; i++) {
                        Player *p = player_manager->Players[i];
                        if (p != target && p->room_id == room_id && !p->is_disconnected) {
                            send_game_message(server_sock, &p->addr, CMD_VOTE, notify);
                        }
                    }
                    if (room->is_game_active && room->player_count < 2) {
                        Player *survivor = NULL;
                        for (int i = 0; i < player_manager->count; i++) {
                            Player *p = player_manager->Players[i];
                            if (p->room_id == room_id && !p->is_disconnected) {
                                survivor = p;
                                break;
                            }
                        }

                        if (survivor) {
                            room->is_game_active = false;
                            char win_msg[64];
                            snprintf(win_msg, sizeof(win_msg), "WIN %s", survivor->name);
                            send_game_message(server_sock, &survivor->addr, CMD_VOTE, win_msg);
                        }
                    }

                    pthread_mutex_unlock(&player_manager->mutex);
                    broadcast_room_list_to_lobby(server_sock, player_manager, room_manager);
                    pthread_mutex_lock(&player_manager->mutex);
                }
            } else {
                Player *target = find_player_by_name(player_manager, room->vote_target_name);
                if (target) {
                    target->last_active_time = time(NULL);
                    target->long_inactive = false;
                }

                for (int i = 0; i < player_manager->count; i++) {
                    Player *p = player_manager->Players[i];
                    if (p->room_id == room_id && !p->is_disconnected) {
                        send_game_message(server_sock, &p->addr, CMD_VOTE, "VOTE_END");
                    }
                }
            }

            room->is_vote_active = false;
            memset(room->vote_target_name, 0, sizeof(room->vote_target_name));
            room->vote_kick_count = 0;
            room->vote_wait_count = 0;
        }

        pthread_mutex_unlock(&player_manager->mutex);

        if (pass_turn) {
            next_turn(room, player_manager, server_sock);
        }

        return 1;
    }

    pthread_mutex_unlock(&player_manager->mutex);
    return 0;
}

