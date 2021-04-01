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

@RestController
public class RController { //Previously called PingPongEndpoint
	GRPCClientService grpcClientService;

	//Assigns client service
	@Autowired
	public RController(GRPCClientService grpcClientService) {
		this.grpcClientService = grpcClientService;
	}

	@PostMapping("/MultiplyMatrix")
	public String matrix (@RequestParam(value = "Matrix_A") String matrixA, @RequestParam(value = "Matrix_B") String matrixB, @RequestParam(value = "noOfRows") int noOfRows, @RequestParam(value = "deadline") int deadline){
		//Hands the 2 matrix inputs to the grpc client's "matrixOperations" function so it can process it and provide a response - the number of rows in the matrix is specified so the input can be formatted
		return grpcClientService.matrixMultiplicationOperation(matrixA, matrixB, noOfRows, deadline);
	}
}