package com.example.grpc.client.grpcclient;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;


//Spring request mappings
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

//Allows you to take files as params
import org.springframework.web.multipart.MultipartFile;
import java.io.*;

@RestController
public class RController { //Previously called PingPongEndpoint
	GRPCClientService grpcClientService;

	//Assigns client service
	@Autowired
	public RController(GRPCClientService grpcClientService) {
		this.grpcClientService = grpcClientService;
	}

	@PostMapping("/MultiplyMatracies")
	public String matrix (@RequestParam(value = "Matrix_A") MultipartFile matrixA, @RequestParam(value = "Matrix_B") MultipartFile matrixB, @RequestParam(value = "noOfRows") int noOfRows, @RequestParam(value = "deadline") int deadline){
		if(matrixA.isEmpty() || matrixB.isEmpty()) {
			return "One or both of the matracies files provided are/is empty";
		}

		String matrixAAsString = "";
		String matrixBAsString = "";

		try {
			BufferedReader matrixAReader = new BufferedReader(new InputStreamReader(matrixA.getInputStream()));
			BufferedReader matrixBReader = new BufferedReader(new InputStreamReader(matrixB.getInputStream()));

			while (matrixAReader.ready()) {
				if (matrixAAsString.equals("")) {
					matrixAAsString += matrixAReader.readLine();
				} else {
					matrixAAsString += "," + matrixAReader.readLine();
				}
			}

			while (matrixBReader.ready()) {
				if (matrixBAsString.equals("")) {
					matrixBAsString += matrixBReader.readLine();
				} else {
					matrixBAsString += "," + matrixBReader.readLine();
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		//Hands the 2 matrix inputs to the grpc client's "matrixOperations" function so it can process it and provide a response - the number of rows in the matrix is specified so the input can be formatted
		return grpcClientService.matrixMultiplicationOperation(matrixAAsString, matrixBAsString, noOfRows, deadline);
	}

	@PostMapping("/AddMatracies")
	public String matrix (@RequestParam(value = "Matrix_A") MultipartFile matrixA, @RequestParam(value = "Matrix_B") MultipartFile matrixB, @RequestParam(value = "noOfRows") int noOfRows, @RequestParam(value = "deadline") int deadline){
		if(matrixA.isEmpty() || matrixB.isEmpty()) {
			return "One or both of the matracies files provided are/is empty";
		}

		String matrixAAsString = "";
		String matrixBAsString = "";

		try {
			BufferedReader matrixAReader = new BufferedReader(new InputStreamReader(matrixA.getInputStream()));
			BufferedReader matrixBReader = new BufferedReader(new InputStreamReader(matrixB.getInputStream()));

			while (matrixAReader.ready()) {
				if (matrixAAsString.equals("")) {
					matrixAAsString += matrixAReader.readLine();
				} else {
					matrixAAsString += "," + matrixAReader.readLine();
				}
			}

			while (matrixBReader.ready()) {
				if (matrixBAsString.equals("")) {
					matrixBAsString += matrixBReader.readLine();
				} else {
					matrixBAsString += "," + matrixBReader.readLine();
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		//Hands the 2 matrix inputs to the grpc client's "matrixOperations" function so it can process it and provide a response - the number of rows in the matrix is specified so the input can be formatted
		return grpcClientService.matrixAdditionOperation(matrixAAsString, matrixBAsString, noOfRows, deadline);
	}
}