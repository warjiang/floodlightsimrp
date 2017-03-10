package net.floodlightcontroller.intercontroller;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateJson {
	public static void createNIBJson() throws JsonGenerationException, JsonMappingException, IOException{
		File file = new File("src/main/java/net/floodlightcontroller/intercontroller/JSON/NIB.json");
		if(!file.exists())
			file.createNewFile();
		ObjectMapper mapper = new ObjectMapper();  
		while(InterSocket.NIBwriteLock ){
			;
		}
		InterSocket.NIBwriteLock = true; //lock NIB
		mapper.writeValue(file, InterSocket.NIB);  	
		InterSocket.NIBwriteLock = false; //lock NIB
		
	}
	
	public static void createRIBJson() throws JsonGenerationException, JsonMappingException, IOException{
		File file = new File("src/main/java/net/floodlightcontroller/intercontroller/JSON/RIB.json");
		if(!file.exists())
			file.createNewFile();
	/*	else{
			file.delete();
			file.createNewFile();
		}*/
		ObjectMapper mapper = new ObjectMapper();  
		while(InterSocket.RIBwriteLock ){
			;
		}
		InterSocket.RIBwriteLock = true; //lock RIB
		mapper.writeValue(file, InterSocket.curRIB);  
		InterSocket.RIBwriteLock = false; //unlock RIB
	}
	
	public static void createPIBJson() throws JsonGenerationException, JsonMappingException, IOException{
		File file = new File("src/main/java/net/floodlightcontroller/intercontroller/JSON/PIB.json");
		if(!file.exists())
			file.createNewFile();
		ObjectMapper mapper = new ObjectMapper();  
		while(InterSocket.PIBwriteLock ){
			;
		}
		InterSocket.PIBwriteLock = true; //lock PIB
		mapper.writeValue(file, InterSocket.PIB);  
		InterSocket.PIBwriteLock = false; //unlock PIB			
	}

}
