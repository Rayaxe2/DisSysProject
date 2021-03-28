//location of file
package com.example.grpc.client.grpcclient;

/*
import com.example.grpc.server.grpcserver.PingRequest;
import com.example.grpc.server.grpcserver.PongResponse;
import com.example.grpc.server.grpcserver.PingPongServiceGrpc;
*/

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

//import com.myImplementation.grpc...

@Service
public class GRPCClientService {

	public String matrixOperations(String mA, String mB, String dimentions){
		//"localhost" is the IP of the server we want to connect to - 9090 is it's port
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090).usePlaintext().build();
		//create a stub and pass the channel in as a variable
		PingPongServiceGrpc.PingPongServiceBlockingStub stub = MatrixMultServiceGrpc.newBlockingStub(channel);

		//The rest controller gives the array as a string of numbers seperated by commas, we put that in a 1d string array first
		String[] a1D = mA.split(",");
		String[] b1D = mb.split(",");

		//Dimentions of the matrix - th number of rows will always be the same as the number of columns
		int dim = Integer.parseInt(dimentions);

		//Limit it to dimentions to the power of 2? ...
		if(!(a1D.length ==  (dim * dim) || b1D.length == (dim * dim))){
			multiplyBlockResponse reply = stub.multiplyBlock(
					multiplyBlockRequest.newBuilder()
							.clearMatrixC()
							.setC("One or both of the matricies are/is not square")
							.build()
			);
			return reply.getMatrixC();
		}

		//Container for array once split into an array of arrays/2d array
		int[][] a2D = new int[dim][dim];
		int[][] b2D = new int[dim][dim];

		//Splits 1D array into 2D array with dim arrays and dim elements in each array
		for (int i = 0; i < dim; i++) {
			for (int j = 0; j < dim; j++) {
				a2D[i][j] = Integer.parseInt(a1D[(i * dim) + j]);
				b2D[i][j] = Integer.parseInt(b1D[(i * dim) + j]);
			}
		}

		//get a response by calling the multiplyBlockRequest from the stub
		multiplyBlockResponse reply = stub.multiplyBlock(
				multiplyBlockRequest.newBuilder()
						.clearMatrixA()
						.clearMatrixB()
						.addAllMatrixA(TwoDimArrayToTwoDimList(a2D))
						.addAllMatrixB(TwoDimArrayToTwoDimList(b2D))
						.build()
		);

		//Closes channel
		channel.shutdown();

		//Makes funciton return the result of the operation/service
		return reply.getMatrixC(); //Matrix C is a string - change?
	}

	//Packs 2D array into list of repeated
	static List<com.grpc.myImplementation.array> TwoDimArrayToTwoDimList(int A[][])
	{
		//Makes new array list that conforms to the repeated "array" messages data strcuture (where array is repeated ints) in proto
		List<com.grpc.myImplementation.array> listA = new ArrayList<com.grpc.lab1.array>();

		//Goes through each array within the 2d array and adds them to the list construct created prior
		for(int[] innerArray : A)
		{
			//Creates a builder for the "com.grpc.myImplementation.array" objects we want to store in a list
			com.grpc.myImplementation.array.Builder arraySubSet = com.grpc.myImplementation.array.newBuilder();
			//converts array of ints into a list of Integer objects and then adds them to the builder
			arraySubSet.addAllItem(array2List(innerArray));
			//Adds the built object to the previously declare list
			listA.add(arraySubSet.build());
		}
		return listA;
	}

	//Converts int arrays into interget lists
	static List<Integer> array2List(int A[])
	{
		List<Integer> listA = new ArrayList<Integer>();
		for(int i = 0; i<(A.length); i++)
		{
			listA.add(A[i]);
		}
		return listA;
	}
}
