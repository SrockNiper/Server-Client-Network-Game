
#ifndef UPS_MESSAGE_QUEUE_H
#define UPS_MESSAGE_QUEUE_H

#include <pthread.h>
#include <netinet/in.h>

#define MAX_PACKET_DATA 1024

typedef struct Packet {
    struct sockaddr_in sender;
    char data[MAX_PACKET_DATA];
    int length;
    struct Packet *next;
} Packet;

typedef struct MessageQueue {
    Packet *head;
    Packet *tail;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
    int running;
} MessageQueue;

int init_message_queue(MessageQueue *queue);
void destroy_message_queue(MessageQueue *queue);
int enqueue_packet(MessageQueue *queue, struct sockaddr_in *sender, const char *data, int length);
Packet* dequeue_packet_timed(MessageQueue *queue, int timeout_seconds);
void signal_queue_shutdown(MessageQueue *queue);

#endif //UPS_MESSAGE_QUEUE_H

