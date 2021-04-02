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
	//List of server IPs for stubs to connect to
	String[] serverIPs = new String[]{"34.203.38.53", "3.94.181.403", "3.83.226.8", "54.208.88.73", "54.174.173.230", "54.236.246.232", "54.166.38.17", "3.91.176.84"};

	//Breaks matricies into 2x2 blocks, and groups the relevant set of blocks that are needed to work out a block of the final matrix
	//A pool of threads are assigned the task of making multiplication and addition gRPC function calls to certain servers, via a stub,
	//With the groups of blocks - each thread works out a block of the final matrix. The result is unpacks and formated as a string
	public String matrixMultiplicationOperation(String mA, String mB, int dimentions, int deadline) {

		//The IP in serverIPs[0] is the IP of the server we are gong to connect to - 9090 is it's port
		ManagedChannel channel = ManagedChannelBuilder.forAddress(serverIPs[0], 9090).usePlaintext().build();
		//We create a stub and pass the channel in as a parameter to link it to the server
		MatrixMultServiceGrpc.MatrixMultServiceBlockingStub stub = MatrixMultServiceGrpc.newBlockingStub(channel);

		//The rest controller gives a string of numbers seperated by commas, we put them in a 1d string array first
		String[] a1D = mA.split(",");
		String[] b1D = mB.split(",");

		//Stores the provided dimentions of the matrix - the number of rows will always be the same as the number of columns
		int dim = dimentions;

		//Makes the sure matrix is square - if it is not, an error message will be returned to the rest controller
		if (!(a1D.length == (dim * dim) && b1D.length == (dim * dim))) {
			channel.shutdown();
			return "Error: Make sure both matracies have the dimentions " + Integer.toString(dim) + "x" + Integer.toString(dim);
		}

		//Also checks that the matracies' dimentions are a mutliple of 2 since we want n^2 matracies only - so we can break them down
		//Into a series of 2x2 blocks - which you can do with al even n x n matracies that are square
		else if(!(dim % 2 == 0)){
			channel.shutdown();
			return "Error: The Matracies must be n^2 large - where n is an even number";
		}

		//We then converts the string arraysm a1D and b1D, to 1d int arrays
		int[] a1DInt = new int[a1D.length];
		int[] b1DInt = new int[b1D.length];
		try {
			for (int i = 0; i < a1DInt.length; i++) {
				a1DInt[i] = Integer.parseInt(a1D[i]);
				b1DInt[i] = Integer.parseInt(b1D[i]);
			}
		}
		//Throws error and returns error message if the provided rest controller matrix inputs are nin the incorrect format
		catch (final NumberFormatException e) {
			return "Error: Make sure that the matrix is composed of only numbers, commas and new lines and is in the correct format";
		}

		//We them split the 1d int array into a 2d array of size dim x dim (dim being the provided dimentions)
		//Which makes it easier to group the matrix into blocks
		int[][] a2D = new int[dim][dim];
		int[][] b2D = new int[dim][dim];
		for (int i = 0; i < dim; i++) {
			for (int j = 0; j < dim; j++) {
				a2D[i][j] = Integer.parseInt(a1D[(i * dim) + j]);
				b2D[i][j] = Integer.parseInt(b1D[(i * dim) + j]);
			}
		}

		/*
		//Used for debugging
		System.out.println("\nMatrix A:");
		System.out.println(Arrays.deepToString(a2D));

		//Used for debugging
		System.out.println("\na1DInt :");
		System.out.println(Arrays.toString(a1DInt) + "\n");
		 */

		//Splits the 2 input matricies into a series of 2x2 blocks and stores them in a 4D array
		//The 4th dimention stores an array/the set of blocks in MatrixA and an array/the set of block in MatrixB
		//The 3rd dimention stores the Array of blocks for each Matrix
		//The 2nd and 1st dimention store the blocks (2x2 arrays)
		int[][][][] allBlocks = matrixToBlockList(a1DInt, b1DInt);

		/*
		//Used for debugging
		System.out.println("\nallBlocks :");
		System.out.println(Arrays.deepToString(allBlocks));
		 */

		//This list is used to groups blocks that are needed for a single multiplication operation -
		//Integer[][] is for a single block
		//List<Integer[][]> is for a series of blocks
		//List<List<Integer[][]>> stores different series of blocks

		//E.G. To Multiply 2 4x4 matricies, 4 operations need to be done (on the 4 2x2 blocks in the matrix)
		//Each operation will need 4 blocks (2 specifc ones from A and 2 specifc ones from from B) to be multiplied to get an anwser
		//So 16 blocks will be needed (16 List<Integer[][]>), 4 for each atomic operation (4 List<List<Integer[][]>>)
		List<List<Integer[][]>> atomicBlockOPQueue = new ArrayList<List<Integer[][]>>();

		//The dimentions of the array in terms of 2x2 blocks is determined by the number of blocks sqaured and stored
		int blockDim =  (int) Math.sqrt(allBlocks[0].length);

		//This will contain pairs of specific blocks from matrix A and specific blocks from matix B which need to be multiplied by eachother
		//If we want the result of multiplying the 2nd block of 3 block by 3 block structure (first row 2nd column in the block matrix)
		//We would retrive/store all the blocks in the first row ofr the block matrix and all the blocks of the second row -
		//Pairing them in the process to indicate which block a certain block needs to be multiplied by
		//So in terms of coordinates (row, col) of the 3x3 structure the pairs would be (0,1), (0,0) - (1,1), (0,1) - (2,1), (0,2)
		//Since we multiply block (0,1) from matrix A by block B in matrix A (0,0) and add that to the product of multiplying
		//block (1,1) from matrix A by block (0,1) in matrix B - and so on...
		List<Integer[][]> newQueue;

		/*
		//Used for debugging
		System.out.println("\nAllBlocks outer Size: " + allBlocks.length);
		System.out.println("AllBlocks inner Size: " + allBlocks[0].length);
		System.out.println("blockDim: " + blockDim + "\n");
		 */

		//Used to determine the coordonates/possitions of the blocks we want to pair
		int currentCol = 0;
		int currentRow = 0;

		//Selects the specific pair of blocks needed to calculate a block of the final matrix
		for (int a = 0; a < allBlocks[0].length; a++) {
			currentCol = (a % blockDim);
			if(a % blockDim == 0 && a != 0){
				currentRow += 1;
			}
			newQueue = new ArrayList<>();
			for (int i = 0; i < blockDim; i++) {
				newQueue.add(IntArrayToIntergerArray(allBlocks[1][currentCol + (i * blockDim)]));
				newQueue.add(IntArrayToIntergerArray(allBlocks[0][(currentRow * blockDim) + i]));

				/*
				//Used for debugging
				//System.out.println("\nIndex for B " + (a + 1) + "(Pair " + (i + 1) + ")" + ":\n " + ((currentRow * blockDim) + i));

				System.out.println("\nBlock from A " + (a + 1) + "(Pair " + (i + 1) + ")" + ": ");
				System.out.println(Arrays.deepToString(allBlocks[1][currentCol + (i * blockDim)]));

				System.out.println("\nBlock from B " + (a + 1) + "(Pair " + (i + 1) + ")" + ": ");
				System.out.println(Arrays.deepToString(allBlocks[0][(currentRow * blockDim) + i]));
				 */

			}
			//Adds each set of pairs of blocks that need to be multiplied to produce a single block into the list
			//To form a queue of operations
			atomicBlockOPQueue.add(newQueue);
		}

		/*
		//Used for debugging
		System.out.println("\n" + "atomicBlockOPQueue Size: " + atomicBlockOPQueue.size());
		 */

		//Stores the time before a gRPC functiona call
		long startTime = System.nanoTime();
		//gets a response by calling the multiplyBlockRequest from the stub with an arbirtrary block input from the queue
		//The result is discarded
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
		long footprint = (endTime - startTime);
		//Calculates an estimate for the amount of servers needed to meet the deadline given the amount of operations that will be needed
		//((blockDim * 2) * (blockDim * blockDim)) - every block in the final matrix will have been produced through (blockDim * 2) block multiplications/grpc calls,
		//And there are blockDim * blockDim blocks in the matrix - this this will be the numver of multiplication operations that will take place
		int serversNeeded = (int) Math.ceil(((double) footprint * (((double) blockDim * 2.0) * ((double) blockDim * (double) blockDim))) / (double) ((double) deadline * 1000000000.0)); //1 second = 1 million nano second - deadline is in seconds

		//Caps the amount of servers that can be allocated to 8
		if (serversNeeded > 8) {
			serversNeeded = 8;
		}
		//At least 1 server must be available - so 1 is assigned at the very least
		else if (serversNeeded == 0) {
			serversNeeded = 1;
		}

		/*
		//Used for debugging
		//double test = (int) Math.ceil(((double) footprint * (((double) blockDim * (double) 2) * ((double) blockDim * (double) blockDim))));
		System.out.println("Servers Needed: " + serversNeeded + "\nFootprint: " + footprint + "\nDeadline: " + deadline + "\n");
		//+ "\nNo of Block Operations: " + String.valueOf(test)
		 */

		//Closes the channel we opened for testing
		channel.shutdown();

		//These theards will be used to send gRPC service requests concurrently
		//(eliminating the need to wait for a response before sending another request)
		ExecutorService serverThreadPool = Executors.newFixedThreadPool(serversNeeded);

		//Stores of list/pool of stubs for the threads to use
		List<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub> listOfStubs = new ArrayList<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub>();
		//Creates "serversNeeded" many stubs and stores them in the aforementioned list
		for (int i = 0; i < serversNeeded; i++) {
			listOfStubs.add(MatrixMultServiceGrpc.newBlockingStub(
					ManagedChannelBuilder.forAddress(serverIPs[i], 9090).usePlaintext().build())
			);
		}

		//This will store the results of each thread - a list of futures that will hold a block of the final matrix
		//Once we have built the list of futures, we can wait for the threads to work out the results and build the final matrix
		List<Future<List<com.myImplementation.grpc.array>>> futureResults = new ArrayList<Future<List<com.myImplementation.grpc.array>>>();

		//For each set of blocks we need to work on we send the list of available stubs and the list of blocks to be used for the proccess
		for (int i = 0; i < atomicBlockOPQueue.size(); i++) {
			/*
			//Used for debugging
			System.out.println("Future no: " + (i + 1));
			 */

			//The pool of stubs and set of relevant blocks are sent to be used for the multiplication operations,
			//Which the thread pool executes. The results are collected and stored in the list of future, "futureResults"
			futureResults.add(
					serverThreadPool.submit(
							new gRPCBlockMultiplication(listOfStubs, atomicBlockOPQueue.get(i))
					)
			);
		}

		//Used to format the results (which are recieved as features that contain List<com.myImplementation.grpc.array>) back to a series of 2x2 blocks
		List<Integer[][]> listOfResults = new ArrayList<Integer[][]>();

		//Places the resulting matrix blocks into a list of blocks
		for (int i = 0; i < futureResults.size(); i++) {
			/*
			//Used for debugging
			System.out.println("Futures Size: " + futureResults.size() + " - Loop: " + i);
			*/

			try {
				/*
				//Used for debugging
				//System.out.println("listOfResults: " + Arrays.deepToString(listUnpack(futureResults.get(i).get())));
				*/

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

		//Shuts down the pool of threads once we have fetched the results
		serverThreadPool.shutdownNow();

		//Makes the funciton return the result of the operation/service to the rest controller - which calls it
		return listOfBlocksToString(listOfResults); //listOfBlocksToString converts the lists of blocks into a formatted string
	}

	//Similar to matrixMultiplication, however it simply Splits the 2 matracies into blocks and makes a pool of threads
	//Which are used add each coresponding blocks to eachother to form a final block matrix which is then formated into a string
	public String matrixAdditionOperation(String mA, String mB, int dimentions, int deadline) {
		//The IP in serverIPs[0] is the IP of the server we want to connect to - 9090 is it's port
		ManagedChannel channel = ManagedChannelBuilder.forAddress(serverIPs[0], 9090).usePlaintext().build();
		//We create a stub and pass the channel in as a parameter to link it to the server
		MatrixMultServiceGrpc.MatrixMultServiceBlockingStub stub = MatrixMultServiceGrpc.newBlockingStub(channel);

		//The rest controller gives a string of numbers seperated by commas, we put them in a 1d string array first
		String[] a1D = mA.split(",");
		String[] b1D = mB.split(",");

		//Stores the provided dimentions of the matrix - the number of rows will always be the same as the number of columns
		int dim = dimentions;

		//Makes the sure matrix is square - if it is not, an error message will be returned to the rest controller
		if (!(a1D.length == (dim * dim) && b1D.length == (dim * dim))) {
			channel.shutdown();
			return "Error: Make sure both matracies have the dimentions " + Integer.toString(dim) + "x" + Integer.toString(dim);
		}

		//Also checks that the matracies' dimentions are a mutliple of 2 since we want n^2 matracies only - so we can break them down
		//Into a series of 2x2 blocks - which you can do with al even n x n matracies that are square
		else if(!(dim % 2 == 0)){
			channel.shutdown();
			return "Error: The Matracies must be n^2 large - where n is an even number";
		}

		//We then converts the string arraysm a1D and b1D, to 1d int arrays
		int[] a1DInt = new int[a1D.length];
		int[] b1DInt = new int[b1D.length];
		try {
			for (int i = 0; i < a1DInt.length; i++) {
				a1DInt[i] = Integer.parseInt(a1D[i]);
				b1DInt[i] = Integer.parseInt(b1D[i]);
			}
		}
		//Throws error and returns error message if the provided rest controller matrix inputs are nin the incorrect format
		catch (final NumberFormatException e) {
			return "Error: Make sure that the matrix is composed of only numbers, commas and new lines and is in the correct format";
		}


		//We them split the 1d int array into a 2d array of size dim x dim (dim being the provided dimentions)
		//Which makes it easier to group the matrix into blocks
		int[][] a2D = new int[dim][dim];
		int[][] b2D = new int[dim][dim];
		for (int i = 0; i < dim; i++) {
			for (int j = 0; j < dim; j++) {
				a2D[i][j] = Integer.parseInt(a1D[(i * dim) + j]);
				b2D[i][j] = Integer.parseInt(b1D[(i * dim) + j]);
			}
		}

		//Splits the 2 input matricies into a series of 2x2 blocks and stores them in a 4D array
		//The 4th dimention stores an array/the set of blocks in MatrixA and an array/the set of block in MatrixB
		//The 3rd dimention stores the Array of blocks for each Matrix
		//The 2nd and 1st dimention store the blocks (2x2 arrays)
		int[][][][] allBlocks = matrixToBlockList(a1DInt, b1DInt);

		/*
		///for debugging
		System.out.println("\na1D: " + Arrays.toString(a1D));
		System.out.println("\nb1D: " + Arrays.toString(b1D));
		System.out.println("\na2D: " + Arrays.deepToString(a2D));
		System.out.println("\na2D: " + Arrays.deepToString(b2D));
		System.out.println("\na1D: " + Arrays.toString(a1D));
		System.out.println("\nb1D: " + Arrays.toString(b1D));
		 */

		//The dimentions of the array in terms of 2x2 blocks is determined by the number of blocks sqaured and stored
		int blockDim =  (int) Math.sqrt(allBlocks[0].length);

		//Stores the time before gRPC functiona call
		long startTime = System.nanoTime();
		//gets a response by calling the addBlockRequest from the stub with an arbirtrary block input from the queue
		//The result is discarded
		multiplyBlockResponse reply = stub.addBlock(
				multiplyBlockRequest.newBuilder()
						.clearMatrixA()
						.clearMatrixB()
						.addAllMatrixA(TwoDimArrayToTwoDimList(allBlocks[0][0]))
						.addAllMatrixB(TwoDimArrayToTwoDimList(allBlocks[0][0]))
						.build()
		);
		//Stores the time after gRPC functiona call
		long endTime = System.nanoTime();
		//Stores the difference between the start and end time
		long footprint = (endTime - startTime);
		//Calculates an estimate for the amount of servers needed to meet the deadline given the amount of operations that will be needed
		//blockDim * blockDim addictions will be executed to get the result
		int serversNeeded = (int) Math.ceil(((double) footprint * ((double) blockDim * (double) blockDim)) / (double) ((double) deadline * 1000000000.0)); //1 second = 1 million nano second - deadline is in seconds

		//Caps the amount of servers that can be allocated to 8
		if (serversNeeded > 8) {
			serversNeeded = 8;
		}
		//At least 1 server must be available - so 1 is assigned at the very least
		else if (serversNeeded == 0) {
			serversNeeded = 1;
		}

		//Closes the channel we opened
		channel.shutdown();

		//These theards will be used to send gRPC service requests concurrently (eliminating the need to wait for a response before sending another request)
		ExecutorService serverThreadPool = Executors.newFixedThreadPool(serversNeeded);
		//Stores list/pool of stubs for the threads to use
		List<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub> listOfStubs = new ArrayList<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub>();
		//Creates "serversNeeded" many stubs and stores them in the aforementioned list
		for (int i = 0; i < serversNeeded; i++) {
			listOfStubs.add(MatrixMultServiceGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(serverIPs[i], 9090).usePlaintext().build()));
		}

		//This will store the results of each thread
		List<Future<List<com.myImplementation.grpc.array>>> futureResults = new ArrayList<Future<List<com.myImplementation.grpc.array>>>();

		//For each block in both matracies, we make our threadpool add the corresponding blocks in the matracies
		for (int i = 0; i < allBlocks[0].length; i++) {
			//The pool of stubs and the blocks that we want to add are sent to be used for the addition operations,
			//which the thread pool executes. The results are collected and stored in the list of future, "futureResults"
			futureResults.add(
					serverThreadPool.submit(
							new gRPCBlockAddition(listOfStubs, allBlocks[0][i], allBlocks[1][i], i)
					)
			);
		}

		//Used to format the results (which are recieved as features that contain List<com.myImplementation.grpc.array>) back to a series of 2x2 blocks
		List<Integer[][]> listOfResults = new ArrayList<Integer[][]>();

		//Places the resulting matrix blocks into a list of blocks
		for (int i = 0; i < futureResults.size(); i++) {
			try {
				listOfResults.add(
						IntArrayToIntergerArray(listUnpack(futureResults.get(i).get()))
				);
				/*
				//for debugging
				System.out.println("\nlistOfResults:\n " + Arrays.deepToString(listOfResults.get(i)));
				 */
			}
			catch (ExecutionException e){
				e.printStackTrace();
			}
			catch (InterruptedException e){
				e.printStackTrace();
			}
		}

		//Shuts down the server once it's done processing the remaining threads
		serverThreadPool.shutdownNow();

		//Makes the funciton return the result of the operation/service to the rest controller - which calls it
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
			this.unprocessedBlockA = new int[blks.size()/2][blks.get(0).length][blks.get(0)[0].length];
			this.unprocessedBlockB = new int[blks.size()/2][blks.get(0).length][blks.get(0)[0].length];
			//Since the list of blocks are pairs of blocks from matrix A and B, we unpack them into seperate lists
			for (int i = 0; i < blks.size(); i = i + 2) {
				this.unprocessedBlockA[i / 2] = IntergerArrayToIntArray(blks.get(i));
				this.unprocessedBlockB[i / 2] = IntergerArrayToIntArray(blks.get(i + 1));
			}
		}

		//Adds process to the threadpool's queue which takes some of the block provided and mutliplies them with certain other blocks
		//It assigns each process to a different stub on a rotational basis to make sure not all process are sent to the same server (stubPool.get(i % stubPool.size()))
		//The results of the multiplicate are added together via the matrix addition gRCP function
		@Override
		public List<com.myImplementation.grpc.array> call() {
			/*
			//Used for debugging
			System.out.println("\nunprocessedBlockA:\n " + Arrays.deepToString(unprocessedBlockA));
			*/

			//The result of each block multiplication should be accumilated here
			List<com.myImplementation.grpc.array> resultingMatrix = new ArrayList<com.myImplementation.grpc.array>();

			//Goes through each blocks in the operation set (the set of blocks that need to be mutliplied and added)
			for (int i = 0; i < unprocessedBlockA.length; i++) {
				multiplyBlockResponse blockMultiplicationResponse = stubPool.get(i % stubPool.size()).multiplyBlock(
						multiplyBlockRequest.newBuilder()
								.clearMatrixA()
								.clearMatrixB()
								.addAllMatrixA(TwoDimArrayToTwoDimList(unprocessedBlockB[i]))
								.addAllMatrixB(TwoDimArrayToTwoDimList(unprocessedBlockA[i]))
								.build()
				);

				/*
				//Used for debugging
				System.out.println("\nMultResults (" + i + "):\n " + listUnpackToString(blockMultiplicationResponse.getMatrixCList()));
				System.out.println("\nunprocessedBlockA[i]:\n " + Arrays.deepToString(unprocessedBlockA[i]));
				System.out.println("\nunprocessedBlockB[i]:\n " + Arrays.deepToString(unprocessedBlockB[i]));
				 */

				//Accumilates the results of the multiplication into a single block
				if (i > 0) {
					multiplyBlockResponse blockMultiplicationResponse2 = stubPool.get(i % stubPool.size()).addBlock(
							multiplyBlockRequest.newBuilder()
									.clearMatrixA()
									.clearMatrixB()
									.addAllMatrixA(resultingMatrix)
									.addAllMatrixB(blockMultiplicationResponse.getMatrixCList())
									.build()
					);

					//If we have only finished the first calculation, we just store the result as there is nothing to add to
					//otherwise else we do addition, assign the result to blockMultiplicationResponse2 and assign the result here
					resultingMatrix = blockMultiplicationResponse2.getMatrixCList();
				}
				else {
					resultingMatrix = blockMultiplicationResponse.getMatrixCList();
				}

				/*
				//Used for debugging
				System.out.println("\nresultingMatrix:\n " + listUnpackToString(resultingMatrix));
				 */
			}
			return resultingMatrix;
		}
	}

	//Callable thread function for Addition
	static class gRPCBlockAddition implements Callable<List<com.myImplementation.grpc.array>> {
		//Stores the pool of stubs to use to send gRPC functions
		List<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub> stubPool;
		//Stores array of matrix blocks
		int[][] unprocessedBlockA;
		int[][] unprocessedBlockB;

		int stubIndex;

		//Takes arrays of stubs and the blocks in matrix A and B and places them in the above variables
		public gRPCBlockAddition(List<MatrixMultServiceGrpc.MatrixMultServiceBlockingStub> stubs, int[][] mA, int[][] mB, int i) {
			this.stubPool = stubs;
			this.unprocessedBlockA = mA;
			this.unprocessedBlockB = mB;
			this.stubIndex = i;
		}

		//Blocks are added together
		@Override
		public List<com.myImplementation.grpc.array> call() {
			//Calls gRPC function on servers
			multiplyBlockResponse blockMultiplicationResponse = stubPool.get(stubIndex % stubPool.size()).addBlock(
					multiplyBlockRequest.newBuilder()
							.clearMatrixA()
							.clearMatrixB()
							.addAllMatrixA(TwoDimArrayToTwoDimList(unprocessedBlockA))
							.addAllMatrixB(TwoDimArrayToTwoDimList(unprocessedBlockB))
							.build()
			);

			//Returns result
			return blockMultiplicationResponse.getMatrixCList();
		}
	}

	//Useful functions for formating, packing and unpacking
	//<=======================>

	//Converts array A and B into an array of blocks (subsections of the matrix provieded - which are also 2x2 2D arrays)
	//and stores the respective list of blocks into the 4th dimention of the array
	//(The list of blocks in A and B, which are respecitvely stored in a 3d Array, is stored in in a 4D array)
	static int[][][][] matrixToBlockList(int A[], int B[]) {
		int matrixDim = (int) Math.sqrt(A.length);
		//Amount of blocks that matrix can be split into (amount of elements devided by 4 (since blocks are 2x2))
		int blocksInMatrix = A.length / 4;

		//The 2d Arrays will be laid out into a 1D array and stored ere
		int[] TwoToOneDA = A;
		int[] TwoToOneDB = B;

		//The blocks for matrix A and B are stored here
		int[][][] listOfBlocksA = new int[blocksInMatrix][2][2];
		int[][][] listOfBlocksB = new int[blocksInMatrix][2][2];

		//listOfBlocksA and listOfBlocksB are stored here
		int[][][][] listOfBlocksAandB = new int[2][blocksInMatrix][2][2];

		//Blocks will be placed here will retrieved/built
		int[][] tempBlockA = new int[2][2];
		int[][] tempBlockB = new int[2][2];

		int blockNum = 0;
		//Builds blocks
		for (int i = 0; i < matrixDim; i = i + 2) {
			for (int j = 0; j < matrixDim; j = j + 2) {
				listOfBlocksA[blockNum][0][0] = TwoToOneDA[j + (matrixDim * i)];
				listOfBlocksA[blockNum][0][1] = TwoToOneDA[j + 1 + (matrixDim * i)];
				listOfBlocksA[blockNum][1][0] = TwoToOneDA[j + matrixDim + (matrixDim * i)];
				listOfBlocksA[blockNum][1][1] = TwoToOneDA[j + matrixDim + 1 + (matrixDim * i)];

				listOfBlocksB[blockNum][0][0] = TwoToOneDB[j + (matrixDim * i)];
				listOfBlocksB[blockNum][0][1] = TwoToOneDB[j + 1 + (matrixDim * i)];
				listOfBlocksB[blockNum][1][0] = TwoToOneDB[j + matrixDim + (matrixDim * i)];
				listOfBlocksB[blockNum][1][1] = TwoToOneDB[j + matrixDim + 1 + (matrixDim * i)];

				blockNum++;

				/*
				//Used for debugging
				System.out.println("");
				System.out.println("tempBlockB " + (blockNum + 1) + ":\n " + Arrays.deepToString(tempBlockB) + "\n");

				//Used for debugging
				System.out.println("");
				System.out.println("listOfBlocksB Right Now:\n " + Arrays.deepToString(listOfBlocksB) + "\n");
				 */
			}
		}
		/*
		//Used for debugging
		//System.out.println("");
		 */

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
		String rowTwo = "";

		//Creates a seperate string for the first row of block's first rows and also their second rows - which are sequentially one after the other in the full matrix
		for (int i = 0; i < A.size(); i++) {
			//Once a row of blocks has been parsed/processed, we can add the strings in rowOne and rowTwo to the final string with a new line inbetween
			//And repeat the same process on the next row of blocks - this will eventually give us a string that shows the matrix in the right order
			if ((i % ((int) Math.sqrt(A.size()))) == 0 && i != 0) {
				System.out.println("here 1");
				muddledString += "[" + rowOne.substring(0, (rowOne.length() - 2)) + "]\n[" + rowTwo.substring(0, (rowTwo.length() - 2)) + "]\n";
				System.out.println("here 2");
				rowOne = "";
				rowTwo = "";
			}
			rowOne += String.valueOf(A.get(i)[0][0]) + ", " + String.valueOf(A.get(i)[0][1]) + ", ";
			rowTwo += String.valueOf(A.get(i)[1][0]) + ", " + String.valueOf(A.get(i)[1][1]) + ", ";
		}
		muddledString += "[" + rowOne.substring(0, (rowOne.length() - 2)) + "]\n[" + rowTwo.substring(0, (rowTwo.length() - 2)) + "]\n";
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
