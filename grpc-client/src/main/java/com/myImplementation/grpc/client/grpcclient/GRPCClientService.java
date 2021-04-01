package com.example.grpc.client.grpcclient;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.*;
import java.lang.Math;

//Request and response message from proto
import com.myImplementation.grpc.multiplyBlockRequest;
import com.myImplementation.grpc.multiplyBlockResponse;

//Proto builds MatrixServiceGrpc
import com.myImplementation.grpc.MatrixMultServiceGrpc;

@Service
public class GRPCClientService {

	//Breaks matricies into blocks, and orders threads to carry out matrix multiplication with the blocks and returns the result a formatted string
	public String matrixMultiplicationOperation(String mA, String mB, int dimentions, int deadline) {
		//Server IPs
		String[] serverIPs = new String[]{"18.208.144.93", "3.89.220.217", "3.80.26.40", "184.72.110.77", "54.208.143.214", "18.204.206.212", "18.205.29.165", "34.224.94.243"};

		//"localhost" is the IP of the server we want to connect to - 9090 is it's port
		ManagedChannel channel = ManagedChannelBuilder.forAddress(serverIPs[0], 9090).usePlaintext().build();
		//create a stub and pass the channel in as a variable
		MatrixMultServiceGrpc.MatrixMultServiceBlockingStub stub = MatrixMultServiceGrpc.newBlockingStub(channel);

		//The rest controller gives the array as a string of numbers seperated by commas, we put that in a 1d string array first
		String[] a1D = mA.split(",");
		String[] b1D = mB.split(",");

		//Dimentions of the matrix - the number of rows will always be the same as the number of columns
		int dim = dimentions;

		//Make sure matrix is square - if it is not, an error message will be returned to the rest controller
		if (!(a1D.length == (dim * dim) && b1D.length == (dim * dim))) {
			multiplyBlockResponse reply = stub.multiplyBlock(
					multiplyBlockRequest.newBuilder()
							.clearError()
							.setError("One or both of the matricies are/is not square")
							.build()
			);
			channel.shutdown();
			return "Error: Make sure both matracies have the dimentions " + Integer.toString(dim) + "x" + Integer.toString(dim);
		}
		//Also checks that it's dimentions are a mutliple of 2 since we want n^2 matracies only so we can break them down into
		//A series of 2x2 blocks
		else if(!(dim % 2 == 0)){
			multiplyBlockResponse reply = stub.multiplyBlock(
					multiplyBlockRequest.newBuilder()
							.clearError()
							.setError("The matracies are not n^2 large")
							.build()
			);
			channel.shutdown();
			return "Error: The Matracies must be n^2 large - where n is an even number";
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

		//Splits the input matricies a and b into a series of 2x2 blocks and stores them
		//the 4th dimention stores an array of blocks for MatrixA and the set of block fo MatrixB
		//The 3rd dimention stores the Array of blocks
		//The 2nd and 1st dimention store the blocks (2x2 blocks)
		int[][][][] allBlocks = matrixToBlockList(a2D, b2D);

		//Groups blocks that are needed for a single operation -
		//Integer[][] is for a single block, List<Integer[][]> is for a series of blocks, List<List<Integer[][]>> stores the blocks for A and B Seperately
		//E.G. To Multiply 2 4x4 matricies, 4 operations need to be done (on the 4 2x2 blocks in the matrix)
		//Each operation will need 4 blocks (2 specifc ones from A and 2 specifc ones from from B) to be multiplied to get an anwser
		//So 16 blocks will be needed, 4 for each atomic operation
		List<List<Integer[][]>> atomicBlockOPQueue = new ArrayList<List<Integer[][]>>();

		//The dimentions of the array in terms of 2x2 blocks
		int blockDim =  (int) Math.sqrt(allBlocks[0].length);

		//Selects the specific blocks needed to calculate a block of the final matrix
		for (int a = 0; a < allBlocks[0].length; a++) {
			int currentCol = (a % blockDim);
			int currentRow = 0;
			if(a % blockDim == 0){
				currentRow += 1;
			}
			List<Integer[][]> newQueue = new ArrayList<>();
			for (int i = 0; i < blockDim; i++) {
				newQueue.add(IntArrayToIntergerArray(allBlocks[1][currentCol + (i * blockDim)]));
				newQueue.add(IntArrayToIntergerArray(allBlocks[0][(currentRow * blockDim) + i]));
			}
			atomicBlockOPQueue.add(newQueue);
		}

		//Stores the time before gRPC functiona call
		long startTime = System.nanoTime();
		//gets a response by calling the multiplyBlockRequest from the stub
		multiplyBlockResponse reply = stub.multiplyBlock(
				multiplyBlockRequest.newBuilder()
						.clearMatrixA()
						.clearMatrixB()
						.addAllMatrixA(TwoDimArrayToTwoDimList(IntergerArrayToIntArray(atomicBlockOPQueue.get(0).get(0))))
						.addAllMatrixB(TwoDimArrayToTwoDimList(IntergerArrayToIntArray(atomicBlockOPQueue.get(1).get(0))))
						.build()
		);
		//Stores the time after gRPC functiona call
		long endTime = System.nanoTime();
		//Stores the difference between the start and end time
		long footprint = endTime - startTime;
		//Calculates an estimate for the amount of servers needed to meet the deadline given the amount of operations that will be needed
		//((blockDim * 2) * (blockDim * blockDim)) - every block in the final matrix will have been produced through (blockDim * 2) block multiplications/grpc calls, and there are blockDim * blockDim blocks in the matrix
		int serversNeeded = (int) Math.ceil((footprint * ((blockDim * 2) * (blockDim * blockDim))) / deadline);

		//Caps the amount of servers that can be used to 8
		if (serversNeeded > 8) {
			serversNeeded = 8;
		}

		//Closes the channel we opened
		channel.shutdown();


		//These theards will be used to send gRPC service requests concurrently (eliminating the need to wait for a response before sending another request)
		ExecutorService serverThreadPool = Executors.newFixedThreadPool(serversNeeded);
		//Stores of list/pool of stubs for the threads to use
		List<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub> listOfStubs = new ArrayList<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub>();
		//Creates "serversNeeded" many stubs and stores them in the aforementioned list
		for (int i = 0; i < serversNeeded; i++) {
			channel = ManagedChannelBuilder.forAddress(serverIPs[i], 9090).usePlaintext().build();
			listOfStubs.add(MatrixMultServiceGrpc.newBlockingStub(channel));
		}

		//This will store the results of each thread
		List<Future<List<com.myImplementation.grpc.array>>> futureResults = new ArrayList<Future<List<com.myImplementation.grpc.array>>>();

		for (int i = 0; i < atomicBlockOPQueue.size(); i++) {
			//The pool of stubs and set of relevant blocks are sent to be used for the multiplication operations,
			// which the thread pool executes. The results are collected and stored in the list of future, "futureResults"
			futureResults.add(
					serverThreadPool.submit(
							new gRPCBlockMultiplication(listOfStubs, atomicBlockOPQueue.get(i))
					)
			);
		}

		//Shuts down the server once it's done processing the reaming threads
		serverThreadPool.shutdown();

		//Used to format the results, which are recieved as features and multiplyBlockResponses, back to a series of 2x2 blocks
		List<Integer[][]> listOfResults = new ArrayList<Integer[][]>();

		//PLaces the resulting matrix blocks into a list of blocks
		for (int i = 0; i < futureResults.size(); i++) {
			try {
			listOfResults.add(
					IntArrayToIntergerArray(listUnpack(futureResults.get(i).get()))
			);
			}
			catch (ExecutionException e){
				e.printStackTrace();
			}
			catch (InterruptedException e){
				e.printStackTrace();
			}
		}
		//Makes funciton return the result of the operation/service to the rest controller - which calls it
		return listOfBlocksToString(listOfResults); //listOfBlocksToString converts the lits of blocks into a formatted string
	}

	//Callable implementation
	//<=======================>

	//Callable thread function for mutliplication
	static class gRPCBlockMultiplication implements Callable<List<com.myImplementation.grpc.array>> {
		//Stores the pool of stubs to use to send gRPC functions
		List<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub> stubPool;
		//Stores array of matrix blocks
		int[][][] unprocessedBlockA;
		int[][][] unprocessedBlockB;

		//Takes arrays of stubs and the blocks in matrix A and B and places them in the above variables
		public gRPCBlockMultiplication(List<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub> stubs, List<Integer[][]> blks) {
			this.stubPool = stubs;
			for (int i = 0; i < blks.size(); i = i + 2) {
				this.unprocessedBlockA[i / 2] = IntergerArrayToIntArray(blks.get(i));
				this.unprocessedBlockB[i / 2] = IntergerArrayToIntArray(blks.get(i + 1));
			}
		}

		//Adds process to the threadpool's queue which take some of the block provided and mutliplies them with certain other blocks
		//Each process to a different stub on a rotational basis to make sure not all process are sent to the same server (tubPool.get(i % blksA.length))
		//The results of the multiplicate are added together via the matrix addition gRCP function
		@Override
		public List<com.myImplementation.grpc.array> call() {
			//The result of each block multiplication should be accumilated here
			List<com.myImplementation.grpc.array> resultingMatrix = new ArrayList<com.myImplementation.grpc.array>();

			multiplyBlockResponse blockMultiplicationResponse;

			//Goes through each blocks in the operation set (the set of blocks that need to be mutliplied and added)
			for (int i = 0; i < unprocessedBlockA.length; i++) {
				blockMultiplicationResponse = stubPool.get(i % unprocessedBlockA.length).multiplyBlock(
						multiplyBlockRequest.newBuilder()
								.clearMatrixA()
								.clearMatrixB()
								.addAllMatrixA(TwoDimArrayToTwoDimList(unprocessedBlockA[i]))
								.addAllMatrixB(TwoDimArrayToTwoDimList(unprocessedBlockB[i]))
								.build()
				);
				//Accumilates the results of the multiplication into a single block
				if (i > 0) {
					blockMultiplicationResponse = stubPool.get(i % unprocessedBlockA.length).addBlock(
							multiplyBlockRequest.newBuilder()
									.clearMatrixA()
									.clearMatrixB()
									.addAllMatrixA(resultingMatrix)
									.addAllMatrixB(blockMultiplicationResponse.getMatrixCList())
									.build()
					);
				}
				//If we have only finished the first calculation, we just assign the result as there is nothing to add to
				//else we do addition, assign the result to blockMultiplicationResponse and assign the result here
				resultingMatrix = blockMultiplicationResponse.getMatrixCList();
			}
			return resultingMatrix;
		}
	}

	/*
	//Callable thread function for Addition
	static class gRPCBlockAddition implements Callable<multiplyBlockResponse> {
		public gRPCBlockMultiplication() {
		}

		@Override
		public multiplyBlockResponse call() throws Exception {
		}
	}
	*/

	//Useful functions for formating, packing and unpacking
	//<=======================>

	//Converts array A and B into an array of blocks (subsections of the matrix provieded - which are also 2x2 2D arrays)
	//Snd stores the respective list of blocks into the 4th dimention of the array
	//(The list of blocks in A and B, which are respecitvely stored in a 3d Array, is stored in in a 4D array)
	static int[][][][] matrixToBlockList(int A[][], int B[][]) { //, MatrixServiceGrpc.MatrixServiceBlockingStub stub
		int matrixDim = A.length;
		//Amount of blocks that matrix can be split into (amount of elements devided by 4 (since blocks are 2x2))
		int blocksInMatrix = (int) Math.ceil((matrixDim * matrixDim) / 4);

		//The 2d Arrays will be laid out into a 1D array and stored ere
		int[] TwoToOneDA = new int[matrixDim * matrixDim];
		int[] TwoToOneDB = new int[matrixDim * matrixDim];

		//The blocks for matrix A and B are stored here
		int[][][] listOfBlocksA = new int[blocksInMatrix][2][2];
		int[][][] listOfBlocksB = new int[blocksInMatrix][2][2];

		//listOfBlocksA and listOfBlocksB are stored here
		int[][][][] listOfBlocksAandB = new int[2][blocksInMatrix][2][2];

		//Blocks will be placed here will retrieved/built
		int[][] tempBlockA = new int[2][2];
		int[][] tempBlockB = new int[2][2];

		//Puts 2D arrays into 1D Array
		int count = 0;
		for (int i = 0; i < A.length; i++) {
			for (int j = 0; j < A.length; j++) {
				TwoToOneDA[count] = A[i][j];
				TwoToOneDB[count] = B[i][j];
				count += 1;
			}
		}

		//Uses aforementioned formulas to build blocks
		for (int i = 0; i < blocksInMatrix; i++) {
			//Incrememted by 2 since the first 2 elements of the current possition is put into the block per loop
			for (int j = 0; j < listOfBlocksA.length; j = j + 2) {
				tempBlockA[0][0] = TwoToOneDA[j];
				tempBlockB[0][0] = TwoToOneDB[j];

				tempBlockA[0][1] = TwoToOneDA[j + 1];
				tempBlockB[0][1] = TwoToOneDB[j + 1];

				tempBlockA[1][0] = TwoToOneDA[j + matrixDim];
				tempBlockB[1][0] = TwoToOneDB[j + matrixDim];

				tempBlockA[1][1] = TwoToOneDA[j + matrixDim + 1];
				tempBlockB[1][1] = TwoToOneDB[j + matrixDim + 1];
			}
			//Puts blocks into list of blocks
			listOfBlocksA[i] = tempBlockA;
			listOfBlocksB[i] = tempBlockB;
		}

		//Puts lists of blocks into 4D block to return
		listOfBlocksAandB[0] = listOfBlocksA;
		listOfBlocksAandB[1] = listOfBlocksB;

		return listOfBlocksAandB;
	}

	//Converts 2D array matrix blocks into lists that can be passed to the stub
	static List<List<com.myImplementation.grpc.array>> blocksToGRPCList(int A[][], int B[][]) {
		//Makes a list of lists (of the a type the makes what GRPC accepts) to store 2D array A and B
		List<List<com.myImplementation.grpc.array>> matrixLists = new ArrayList<List<com.myImplementation.grpc.array>>();

		//Places aformentioned lists in the aforementioned list of lists construct
		matrixLists.add(TwoDimArrayToTwoDimList(A));
		matrixLists.add(TwoDimArrayToTwoDimList(B));

		return matrixLists;
	}

	//Packs 2D array into list of repeated
	static List<com.myImplementation.grpc.array> TwoDimArrayToTwoDimList(int A[][]) {
		//Makes new array list that conforms to the repeated "array" messages data strcuture (where array is repeated ints) in proto
		List<com.myImplementation.grpc.array> listA = new ArrayList<com.myImplementation.grpc.array>();

		//Goes through each array within the 2d array and adds them to the list construct created prior
		for (int[] innerArray : A) {
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
	static List<Integer> array2List(int A[]) {
		//Creates list to return
		List<Integer> listA = new ArrayList<Integer>();
		//Fills list with the array's content
		for (int i = 0; i < (A.length); i++) {
			listA.add(A[i]);
		}
		return listA;
	}

	//Formats a List<com.myImplementation.grpc.array> into a string that presents a matrix
	static String listUnpackToString(List<com.myImplementation.grpc.array> A) {
		String arrayInString = "[";
		//Adds each element of the string from the first row to last (top to bottom), and from the first column to last (left to right)
		for (int x = 0; x < A.size(); x++) {
			for (int y = 0; y < A.get(x).getItemCount(); y++) {
				//Commas are added between elements
				arrayInString += String.valueOf(A.get(x).getItem(y)) + ", ";
			}
			arrayInString = arrayInString.substring(0, arrayInString.length() - 2);
			//Each row of the matrix is encapsulated by sqaure brackers and are placed on a new line
			arrayInString += "]\n[";
		}

		//remove excess characters ("[" and a new line)
		return arrayInString.substring(0, arrayInString.length() - 2);
	}

	//Puts a List<com.myImplementation.grpc.array> A into a 2d Array of ints
	static int[][] listUnpack(List<com.myImplementation.grpc.array> A) {
		//Matches the array's dimentions to the list's dimentions
		int[][] ArrayA = new int[A.size()][A.get(0).getItemCount()];

		//Adds elements from the list to the array
		for (int x = 0; x < A.size(); x++) {
			for (int y = 0; y < A.get(x).getItemCount(); y++) {
				ArrayA[x][y] = A.get(x).getItem(y);
			}
		}

		return ArrayA;
	}

	//Formats a ist of blocks into a string that presents a matrix
	static String listOfBlocksToString(List<Integer[][]> A) {
		String muddledString = "";
		String rowOne = "";
		String RowTwo = "";

		//Creates a seperate string for the first row of block's first rows and also their second rows - which are sequentially one after the other in the full matrix
		for (int i = 0; i < A.size(); i++) {
			//Once a row of blocks has been parsed/processed, we can add the strings in rowOne and rowTwo to the final string with a new line inbetween
			//And repeat the same process on the next row of blocks - this will eventually give us a string that shows the matrix in the right order
			if (i % ((int) Math.sqrt(A.size())) == 0) {
				muddledString += "[" + rowOne.substring(0, rowOne.length() - 2) + "]\n[" + RowTwo.substring(0, rowOne.length() - 2) + "]\n";
				rowOne = "";
				RowTwo = "";
			} else {
				rowOne += String.valueOf(A.get(i)[0][0]) + ", " + String.valueOf(A.get(i)[0][1]) + ", ";
				RowTwo += String.valueOf(A.get(i)[1][0]) + ", " + String.valueOf(A.get(i)[1][1]) + ", ";
			}
		}
		return muddledString;
	}

	//Converts a 2D int array (int[][]) to a 2D Integer array (Integer[][]) t
	static Integer[][] IntArrayToIntergerArray(int[][] A) {
		Integer[][] result = new Integer[A.length][A[0].length];
		for(int i = 0; i < A.length; i++){
			for(int j = 0; j < A[0].length; j++){
				result[i][j] = Integer.valueOf(A[i][j]);
			}
		}
		return result;
	}

	//Converts a 2D Integer array (Integer[][]) to a 2D int array (int[][])
	static int[][] IntergerArrayToIntArray(Integer[][] A) {
		int[][] result = new int[A.length][A[0].length];
		for(int i = 0; i < A.length; i++){
			for(int j = 0; j < A[0].length; j++){
				result[i][j] = A[i][j].intValue();
			}
		}
		return result;
	}
}
