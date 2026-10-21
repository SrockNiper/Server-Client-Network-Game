//
// Created by mj on 11/9/25.
//
#ifndef UPS_PROTOCOL_H
#define UPS_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>
#include <netinet/in.h>

#define MAGIC "DCG"
#define MAGIC_LEN 3
#define HEADER_SIZE 6  // 3 (magic) + 1 (command) + 2 (delka obsahu)
#define MAX_CONTENT_LENGTH 99

#define CMD_GAME_PASS 3
#define CMD_GAME_REROLL 4
#define CMD_GAME_WIN 5

#define CMD_VOTE 7
#define CMD_PING 9


typedef struct {
    uint8_t command;
    uint16_t content_length;
    char *content;
} Message;

int parse_message(const char *buffer, size_t buffer_len, Message *msg);
void free_message(Message *msg);
int send_game_message(int sock, struct sockaddr_in *addr, int cmd, const char *content);

#endif //UPS_PROTOCOL_H
