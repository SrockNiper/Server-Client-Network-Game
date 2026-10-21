//
// Created by refactoring on 12/21/25.
//

#include "message_queue.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <sys/time.h>

int init_message_queue(MessageQueue *queue) {
    if (!queue) return 0;

    queue->head = NULL;
    queue->tail = NULL;
    queue->running = 1;

    if (pthread_mutex_init(&queue->mutex, NULL) != 0) {
        perror("pthread_mutex_init");
        return 0;
    }

    if (pthread_cond_init(&queue->cond, NULL) != 0) {
        perror("pthread_cond_init");
        pthread_mutex_destroy(&queue->mutex);
        return 0;
    }

    return 1;
}

void destroy_message_queue(MessageQueue *queue) {
    if (!queue) return;

    pthread_mutex_lock(&queue->mutex);

    Packet *current = queue->head;
    while (current) {
        Packet *next = current->next;
        free(current);
        current = next;
    }

    queue->head = NULL;
    queue->tail = NULL;

    pthread_mutex_unlock(&queue->mutex);

    pthread_cond_destroy(&queue->cond);
    pthread_mutex_destroy(&queue->mutex);
}

int enqueue_packet(MessageQueue *queue, struct sockaddr_in *sender, const char *data, int length) {
    if (!queue || !sender || !data || length <= 0 || length > MAX_PACKET_DATA) {
        return 0;
    }

    Packet *packet = malloc(sizeof(Packet));
    if (!packet) {
        perror("malloc Packet");
        return 0;
    }

    memcpy(&packet->sender, sender, sizeof(struct sockaddr_in));
    memcpy(packet->data, data, length);
    packet->length = length;
    packet->next = NULL;

    pthread_mutex_lock(&queue->mutex);

    if (queue->tail) {
        queue->tail->next = packet;
        queue->tail = packet;
    } else {
        queue->head = packet;
        queue->tail = packet;
    }

    pthread_cond_signal(&queue->cond);
    pthread_mutex_unlock(&queue->mutex);

    return 1;
}

Packet* dequeue_packet_timed(MessageQueue *queue, int timeout_seconds) {
    if (!queue) return NULL;

    pthread_mutex_lock(&queue->mutex);

    struct timespec ts;
    struct timeval now;
    gettimeofday(&now, NULL);

    ts.tv_sec = now.tv_sec + timeout_seconds;
    ts.tv_nsec = now.tv_usec * 1000;

    while (!queue->head && queue->running) {
        int result = pthread_cond_timedwait(&queue->cond, &queue->mutex, &ts);
        if (result == ETIMEDOUT) {
            pthread_mutex_unlock(&queue->mutex);
            return NULL;
        }
    }

    if (!queue->running && !queue->head) {
        pthread_mutex_unlock(&queue->mutex);
        return NULL;
    }

    Packet *packet = queue->head;
    if (packet) {
        queue->head = packet->next;
        if (!queue->head) {
            queue->tail = NULL;
        }
        packet->next = NULL;
    }

    pthread_mutex_unlock(&queue->mutex);

    return packet;
}

void signal_queue_shutdown(MessageQueue *queue) {
    if (!queue) return;

    pthread_mutex_lock(&queue->mutex);
    queue->running = 0;
    pthread_cond_broadcast(&queue->cond);
    pthread_mutex_unlock(&queue->mutex);
}

