//
// Created by mj on 11/9/25.
//
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include "config.h"

#define MAX_LINE 256
// Pomocna funkce pro "bezpecne" parsovani integeru(aneb aby prekladac mlcel)
int parse_int(const char *str, int *result) {
    char *endptr;
    errno = 0;
    long val = strtol(str, &endptr, 10);
    if (errno != 0 || endptr == str || *endptr != '\0') {
        return 0;
    }
    *result = (int)val;
    return 1;
}

int load_config(const char *filename, ServerConfig *config) {
    FILE *file = fopen(filename, "r");
    if (!file) {
        printf("Nenalezen konfiguracni soubor\n");
        return 0;
    }

    char line[MAX_LINE];
    while (fgets(line, sizeof(line), file)) {
        char key[64], value[64];
        if (sscanf(line, "%63[^=]=%63s", key, value) == 2) {
            if (strcmp(key, "server_address") == 0) {
                strncpy(config->server_address, value, sizeof(config->server_address) - 1);
                config->server_address[sizeof(config->server_address) - 1] = '\0';
            } else if (strcmp(key, "server_port") == 0) {
                if(!parse_int(value, &config->server_port)){
                    printf("Chyba pri parsovani server_port v konfiguracnim souboru\n");
                    fclose(file);
                    return 0;
                }
            } else if (strcmp(key, "max_lobby_players") == 0) {
                if(!parse_int(value, &config->max_lobby_players)){
                    printf("Chyba pri parsovani max_lobby_players v konfiguracnim souboru\n");
                    fclose(file);
                    return 0;
                }
            } else if (strcmp(key, "max_rooms") == 0) {
                if (!parse_int(value, &config->max_rooms)) {
                    printf("Chyba pri parsovani max_rooms v konfiguracnim souboru\n");
                    fclose(file);
                    return 0;
                }
                if (config->max_rooms > MAX_ROOMS) {
                    config->max_rooms = MAX_ROOMS;
                }
            } else if (strcmp(key, "room_count") == 0) {
                if (!parse_int(value, &config->room_count)) {
                    printf("Chyba pri parsovani room_count v konfiguracnim souboru\n");
                    fclose(file);
                    return 0;
                }
                if (config->room_count > config->max_rooms) {
                    config->room_count = config->max_rooms;
                }
            } else if (strncmp(key, "room_", 5) == 0) {
                char *room_str = key + 5;
                char *underscore = strchr(room_str, '_');
                if (underscore) {
                    *underscore = '\0';
                    int room_num;
                    if (parse_int(room_str, &room_num) &&room_num >= 1 && room_num <= config->room_count) {
                        int idx = room_num - 1;
                        char *param = underscore + 1;
                        if (strcmp(param, "max_players") == 0) {
                            parse_int(value, &config->rooms[idx].max_players);
                        } else if (strcmp(param, "winning_score") == 0) {
                            parse_int(value, &config->rooms[idx].winning_score);
                        }
                    }
                }
            }
        }
    }
    fclose(file);
    return 1;
}

