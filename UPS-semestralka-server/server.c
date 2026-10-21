#include <sys/types.h>
#include <sys/socket.h>
#include <stdio.h>
#include <sys/un.h>
#include <unistd.h>
#include <netinet/in.h>
#include <unistd.h>
#include <stdlib.h>

int main(void)
{
	int server_sock, client_sock, n;
	int server_addr_len, client_addr_len;
	struct sockaddr_in local_addr;
	struct sockaddr_in remote_addr;
	char ch = 'A';

	// zmena - SOCK_DGRAM, datagramove sluzby (nespolehlive, nespojovane)
	server_sock = socket(AF_INET, SOCK_DGRAM, 0);

	memset(&local_addr, 0, sizeof(struct sockaddr_in));

	local_addr.sin_family = AF_INET;
	local_addr.sin_addr.s_addr = inet_addr("127.0.0.1");
	local_addr.sin_port = htons(10000);

	server_addr_len = sizeof(local_addr);

	if (bind(server_sock, (struct sockaddr *)&local_addr, server_addr_len) != 0)
	{
		printf("Bind ERR\n");
		return -1;
	} 
	else
	{
		printf("Bind OK\n");
	}
	
	while (1)
	{
		printf("Server ceka na data\n");

		// vzdy ocekavame nejakou adresu na vstupu - rekneme, jak velkou
		client_addr_len = sizeof(remote_addr);
		n = recvfrom(server_sock, &ch, 1, 0, (struct sockaddr*)&remote_addr, &client_addr_len);
	
		printf("Pripojil se klient\n" );
		printf("Klient poslal = %c\n", &ch);

		// zvysime znak o 1 a chvilku pockame
		ch++;
		sleep(5);

		// pak ho odesleme zpet (na stejnou adresu ze ktere prisel)
		printf("Server odesila = %c\n", ch);
		n = sendto(server_sock, &ch, 1, 0, (struct sockaddr*)&remote_addr, client_addr_len);
	}

	// kdyby se to nekdy melo sanci dostat sem, je potreba socket pochopitelne uzavrit
	
	close(server_sock);
	
	return 0;
}

