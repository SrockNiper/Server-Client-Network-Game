cmake_minimum_required(VERSION 3.10)
project(UPS C)

set(CMAKE_C_STANDARD 11)

add_executable(UPS
        main.c
        config.c
        config.h
        protocol.c
        protocol.h
        player.c
        player.h
        room.c
        room.h
        commands.c
        commands.h
        message_queue.c
        message_queue.h
        reliability.c
        reliability.h
        game_logic.c
        game_logic.h)

# Link pthread library
target_link_libraries(UPS pthread)
