package net.floodlightcontroller.intercontroller;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateJson {
	public static void createNIBJson(){
		String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/"+InterController.myIPstr+"NIB.json";
		File file = new File(fileName);
		if(!file.exists())
			try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		ObjectMapper mapper = new ObjectMapper();  
		while(InterController.NIBWriteLock ){
			;
		}
		InterController.NIBWriteLock = true; //lock NIB
		try {
			mapper.writeValue(file, InterController.NIB);
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  	
		InterController.NIBWriteLock = false; //lock NIB
		
	}
	
	public static void createRIBJson(){
		String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/"+InterController.myIPstr+"RIB.json";
		File file = new File(fileName);
		if(!file.exists())
			try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	/*	else{
			file.delete();
			file.createNewFile();
		}*/
		ObjectMapper mapper = new ObjectMapper();  
		while(InterController.RIBWriteLock ){
			;
		}
		InterController.RIBWriteLock = true; //lock RIB
		try {
			mapper.writeValue(file, InterController.curRIB);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  
		InterController.RIBWriteLock = false; //unlock RIB
	}
	
	public static void createPIBJson() {
		String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/"+InterController.myIPstr+"PIB.json";
		File file = new File(fileName);
		if(!file.exists())
			try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		ObjectMapper mapper = new ObjectMapper();  
		while(InterController.PIBWriteLock ){
			;
		}
		InterController.PIBWriteLock = true; //lock PIB
		try {
			mapper.writeValue(file, InterController.PIB);
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  
		InterController.PIBWriteLock = false; //unlock PIB			
	}

}
