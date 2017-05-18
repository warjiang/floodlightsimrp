package net.floodlightcontroller.intercontroller;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateJson {
	public static void createNIBJson(){
		//String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/"+InterController.myIPstr+"NIB.json";
		String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/NIB.json";
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
			InterController.NIBWriteLock = false;
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			InterController.NIBWriteLock = false;
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			InterController.NIBWriteLock = false;
			e.printStackTrace();
		}  	
		InterController.NIBWriteLock = false; //lock NIB
		
	}
	
	public static void createRIBJson(){
	//	String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/"+InterController.myIPstr+"RIB.json";
		String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/RIB.json";
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
			InterController.RIBWriteLock = false;
			e.printStackTrace();
		}  
		InterController.RIBWriteLock = false; //unlock RIB
	}
	
	public static void createPIBJson() {
		//String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/"+InterController.myIPstr+"PIB.json";
		String fileName = "src/main/java/net/floodlightcontroller/intercontroller/JSON/PIB.json";
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
			mapper.writeValue(file, InterController.myPIB);
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			InterController.PIBWriteLock = false;
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			InterController.PIBWriteLock = false;
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			InterController.PIBWriteLock = false;
			e.printStackTrace();
		}  
		InterController.PIBWriteLock = false; //unlock PIB			
	}

}
