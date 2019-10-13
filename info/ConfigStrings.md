#Config strings

Config strings are a general means of communication from the server to all connected clients.

Each config string can be at most MAX_QPATH characters.

Config strings hold all the index strings, the lightstyles, and misc data like the sky definition and cdtrack.

All of the current configstrings are sent to clients when they connect, and changes are sent to all connected clients.


	CS_NAME = 0;
	CS_CDTRACK = 1;
	CS_SKY = 2;
	CS_SKYAXIS = 3; // %f %f %f format 
	CS_SKYROTATE = 4;
	CS_STATUSBAR = 5; // display program string 
	CS_AIRACCEL = 29; // air acceleration control 
	CS_MAXCLIENTS = 30;
	CS_MAPCHECKSUM = 31; // for catching cheater maps 
	CS_MODELS = 32;
	
	// these are sent over the net as byte so they cannot be blindly increased
	
	CS_SOUNDS = (CS_MODELS + MAX_MODELS); +256
	CS_IMAGES = (CS_SOUNDS + MAX_SOUNDS); +256
	CS_LIGHTS = (CS_IMAGES + MAX_IMAGES); +256
	CS_ITEMS = (CS_LIGHTS + MAX_LIGHTSTYLES); +256
	CS_PLAYERSKINS = (CS_ITEMS + MAX_ITEMS); +256
	CS_GENERAL = (CS_PLAYERSKINS + MAX_CLIENTS); +256
	
	MAX_CONFIGSTRINGS = (CS_GENERAL + MAX_GENERAL);

When the resources are registered by the game (sounds, models..) their index is saved to the config string array, 