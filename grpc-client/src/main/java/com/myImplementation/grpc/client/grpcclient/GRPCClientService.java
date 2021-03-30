//location of file
package com.example.grpc.client.grpcclient;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

import java.util.*;
import java.lang.Math;

//Request and response message from proto
import com.myImplementation.grpc.multiplyBlockRequest;
import com.myImplementation.grpc.multiplyBlockResponse;

//Proto builds MatrixServiceGrpc
import com.myImplementation.grpc.MatrixMultServiceGrpc;

@Service
public class GRPCClientService {

	public String matrixOperations(String mA, String mB, int dimentions){
		//"localhost" is the IP of the server we want to connect to - 9090 is it's port
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090).usePlaintext().build();
		//create a stub and pass the channel in as a variable
		MatrixMultServiceGrpc.MatrixMultServiceBlockingStub stub = MatrixMultServiceGrpc.newBlockingStub(channel);

		//The rest controller gives the array as a string of numbers seperated by commas, we put that in a 1d string array first
		String[] a1D = mA.split(",");
		String[] b1D = mB.split(",");

		//Dimentions of the matrix - th number of rows will always be the same as the number of columns
		int dim = dimentions;

		//Limit it to dimentions to the power of 2? ...
		if(!(a1D.length ==  (dim * dim) && b1D.length == (dim * dim))){
			multiplyBlockResponse reply = stub.multiplyBlock(
					multiplyBlockRequest.newBuilder()
							.clearError()
							.setError("One or both of the matricies are/is not square")
							.build()
			);
			channel.shutdown();
			return "Error: Make sure both matracies have the dimentions " + Integer.toString(dim) + "x" + Integer.toString(dim);
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

		long startTime = System.nanoTime();

		//get a response by calling the multiplyBlockRequest from the stub
		multiplyBlockResponse reply = stub.multiplyBlock(
				multiplyBlockRequest.newBuilder()
						.clearMatrixA()
						.clearMatrixB()
						.addAllMatrixA(TwoDimArrayToTwoDimList(a2D))
						.addAllMatrixB(TwoDimArrayToTwoDimList(b2D))
						.build()
		);

		long endTime = System.nanoTime();
		long footprint = endTime - startTime;
		int serversNeeded = Math.ceil((footprint*numBlockCalls)/deadline);

		//Closes channel
		channel.shutdown();

		//Makes funciton return the result of the operation/service to the rest controller - which calls it
		return listUnpackToString(reply.getMatrixCList());
	}

	static int[][][][] matrixToBlockList(int A[][], int B[][]) { //, MatrixServiceGrpc.MatrixServiceBlockingStub stub
		int matrixDim = A.length;
		//Amount of blocks that matrix can be split into (amount of elements devided by 4 (since blocks are 2x2))
		int blocksInMatrix = (size*size)/4;

		//The 2d Arrays will be laid out into a 1D array and stored ere
		int[] TwoToOneDA = new int[matrixDim*matrixDim];
		int[] TwoToOneDB = new int[matrixDim*matrixDim];

		//The blocks for matrix A and B are stored here
		int[][][] listOfBlocksA = new int[blocksInMatrix][2][2];
		int[][][] listOfBlocksB = new int[blocksInMatrix][2][2];

		//listOfBlocksA and listOfBlocksB are stored here
		int[][][][] listOfBlocksA&B = new int[2][blocksInMatrix][2][2];

		//Blocks will be placed here will retrieved/built
		int[][] tempBlockA = new int[2][2];
		int[][] tempBlockB = new int[2][2];

		//Puts 2D arrays into 1D Array
		int count = 0;
		for(int i = 0; i < A.length; i++) {
			for(int j = 0; j < A.length; j++){
				TwoToOneDA[count] = A[i][j];
				TwoToOneDB[count] = B[i][j];
				count += 1;
			}
		}

		//Uses aforementioned formulas to build blocks
		for(int i = 0; i < blocksInMatrix; i++) {
			//Incrememted by 2 since the first 2 elements of the current possition is put into the block per loop
			for (int j = 0; j < listOfBlocksA.length; j = j + 2){
				tempBlockA[0][0] = listOfBlocksA[j];
				tempBlockB[0][0] = listOfBlocksB[j];

				tempBlockA[0][1] = listOfBlocksA[j+1];
				tempBlockB[0][1] = listOfBlocksB[j+1];

				tempBlockA[1][0] = listOfBlocksA[j+matrixDim];
				tempBlockB[1][0] = listOfBlocksB[j+matrixDim];

				tempBlockA[1][1] = listOfBlocksA[j+matrixDim+1];
				tempBlockB[1][1] = listOfBlocksB[j+matrixDim+1];
			}
			//Puts blocks into list of blocks
			matrixABlocks[i] = tempBlockA;
			matrixBBlocks[i] = tempBlockB;
		}

		//Puts lists of blocks into 4D block to return
		listOfBlocksA&B[0] = listOfBlocksA;
		listOfBlocksA&B[1] = listOfBlocksB;

		return listOfBlocksA;
	}

	//Converts 2D array matrix blocks into lists that can be passed to the stub
	static List<List<com.myImplementation.grpc.array>> blocksToGRPCList(int A[][], int B[][]){
		//Makes a list of lists (of the a type the makes what GRPC accepts) to store 2D array A and 2D array B
		List<List<com.myImplementation.grpc.array>> matrixLists = new List<List<com.myImplementation.grpc.array>>();

		//The 2 respective 2D arrays will be placed into these lists
		List<List<com.myImplementation.grpc.array>> lA = List<List<com.myImplementation.grpc.array>>();
		List<List<com.myImplementation.grpc.array>> lB = List<List<com.myImplementation.grpc.array>>();

		//Places aformentioned lists in the aforementioned list of lists construct
		matrixLists.add(TwoDimArrayToTwoDimList(lA));
		matrixLists.add(TwoDimArrayToTwoDimList(lB));

		return matrixLists;
	}

	//<=====================================>
	//List packing and unpacking

	//Packs 2D array into list of repeated
	static List<com.myImplementation.grpc.array> TwoDimArrayToTwoDimList(int A[][])
	{
		//Makes new array list that conforms to the repeated "array" messages data strcuture (where array is repeated ints) in proto
		List<com.myImplementation.grpc.array> listA = new ArrayList<com.myImplementation.grpc.array>();

		//Goes through each array within the 2d array and adds them to the list construct created prior
		for(int[] innerArray : A)
		{
			//Creates a builder for the "com.myImplementation.grpc.array" objects we want to store in a list
			com.myImplementation.grpc.array.Builder arraySubSet = com.myImplementation.grpc.array.newBuilder();
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

	static String listUnpackToString(List<com.myImplementation.grpc.array> A)
	{
		String arrayInString = "[";

		for (int x = 0; x < A.size(); x++)
		{

			for (int y = 0; y < A.get(x).getItemCount(); y++)
			{
				arrayInString += String.valueOf(A.get(x).getItem(y)) + ", ";
			}
			arrayInString = arrayInString.substring(0, arrayInString.length() - 2);
			arrayInString += "]\n[";
		}

		return arrayInString.substring(0, arrayInString.length() - 2);
	}
}
