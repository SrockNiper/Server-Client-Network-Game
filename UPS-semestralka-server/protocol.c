//
// Created by mj on 11/9/25.
//
#include "protocol.h"
#include <string.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <stdio.h>
#include <ctype.h>
#include <sys/socket.h>
int parse_message(const char *buffer, size_t buffer_len, Message *msg) {
    if (!msg) return 0;

    msg->command = 0;
    msg->content_length = 0;
    msg->content = NULL;

    if (buffer_len < HEADER_SIZE) {
        return 0;
    }

    if (memcmp(buffer, MAGIC, MAGIC_LEN) != 0) {
        return 0;
    }

    if (!isdigit(buffer[MAGIC_LEN])) {
        return 0;
    }
    msg->command = (uint8_t)(buffer[MAGIC_LEN] - '0');

    if (!isdigit(buffer[4]) || !isdigit(buffer[5])) {
        return 0;
    }
    msg->content_length = (buffer[4] - '0') * 10 + (buffer[5] - '0');

    size_t expected_len = (size_t)HEADER_SIZE + (size_t)msg->content_length;

    if (buffer_len > expected_len) {
        size_t diff = buffer_len - expected_len;
        if (diff == 1 && buffer[buffer_len - 1] == '\n') {
            buffer_len = buffer_len-1;
        } else if (diff == 2 && buffer[buffer_len - 2] == '\r' && buffer[buffer_len - 1] == '\n') {
            buffer_len -= 2;
        } else {
            return 0;
        }
    }

    if (expected_len != buffer_len) {
        return 0;
    }

    if (msg->content_length > 0) {
        msg->content = malloc(msg->content_length + 1);
        if (!msg->content) {
            return 0;
        }

        memcpy(msg->content, buffer + HEADER_SIZE, msg->content_length);
        msg->content[msg->content_length] = '\0';
    }

    return 1;
}


void free_message(Message *msg) {
    if (msg && msg->content) {
        free(msg->content);
        msg->content = NULL;
    }
}

int send_game_message(int sock, struct sockaddr_in *addr, int cmd, const char *content) {
    if (!addr || cmd < 0 || cmd > 9) {
        return 0;
    }

    char buffer[HEADER_SIZE + MAX_CONTENT_LENGTH + 1];
    size_t content_len = 0;

    if (content) {
        content_len = strlen(content);
        if (content_len > MAX_CONTENT_LENGTH) {
            content_len = MAX_CONTENT_LENGTH;
        }
    }

    memcpy(buffer, MAGIC, MAGIC_LEN);
    buffer[MAGIC_LEN] = '0' + (char)cmd;
    buffer[MAGIC_LEN + 1] = '0' + (char)(content_len / 10);
    buffer[MAGIC_LEN + 2] = '0' + (char)(content_len % 10);

    if (content_len > 0) {
        memcpy(buffer + HEADER_SIZE, content, content_len);
    }

    ssize_t sent = sendto(sock, buffer, HEADER_SIZE + content_len, 0,
                          (struct sockaddr*)addr, sizeof(*addr));

    return (sent > 0) ? 1 : 0;
}
