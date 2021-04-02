package com.example.grpc.server.grpcserver;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

//Name of proto request and repsonse messages
import com.myImplementation.grpc.multiplyBlockRequest;
import com.myImplementation.grpc.multiplyBlockResponse;

//matrixMultServiceImpBase generated from proto file
import com.myImplementation.grpc.MatrixMultServiceGrpc.MatrixMultServiceImplBase;

/*
import com.myImplementation.grpc.server;
import com.myImplementation.grpc.MatrixMultService;
*/

import io.grpc.stub.StreamObserver;

import java.util.*;


@GrpcService
public class MatrixMult extends MatrixMultServiceImplBase {
	//overide the multiplyBlock function
	@Override
	//StreamObserver<multiplyBlockResponse> reply lets you do async communication if you want
	public void multiplyBlock(multiplyBlockRequest request, StreamObserver<multiplyBlockResponse> reply) {
		//If the matricies are not square then the reply informs the user
		if (request.getError().equals("One or both of the matricies are/is not square")) {
			multiplyBlockResponse.Builder response = multiplyBlockResponse.newBuilder();
			reply.onNext(response.build());
			reply.onCompleted();
		}
		//If the matricies are not n^2 such that n is an even number, the reply informs the user
		else if (request.getError().equals("The matracies are not n^2 large")) {
			multiplyBlockResponse.Builder response = multiplyBlockResponse.newBuilder();
			reply.onNext(response.build());
			reply.onCompleted();
		}
		else {
			//Puts the matracies from the request data into lists
			List<com.myImplementation.grpc.array> listA = request.getMatrixAList();
			List<com.myImplementation.grpc.array> listB = request.getMatrixBList();

			//Places the List<com.myImplementation.grpc.array> data into a 2D array to make it easier to handle
			int[][] Array1 = listUnpack(listA);
			int[][] Array2 = listUnpack(listB);

			//Mutliplies the matracies (which will always be 2x2 blocks)
			int[][] multProduct = multiplyBlockAux(Array1, Array2);

			//Print out a message saying you received a message from the client - for the server to see/display
			System.out.println("Multiplication Request recieved from client:\n" + request);

			//builds a response
			multiplyBlockResponse.Builder response = multiplyBlockResponse.newBuilder();

			//Converts 2D array structure back into the List<com.myImplementation.grpc.array>, since 2D arrays can not be send in requests and responses
			//But List<com.myImplementation.grpc.array> structures can
			for (int[] innerArray : multProduct) {
				com.myImplementation.grpc.array.Builder arraySubSet = com.myImplementation.grpc.array.newBuilder();
				arraySubSet.addAllItem(array2List(innerArray));
				response.addMatrixC(arraySubSet.build());
			}
			//specifies we want to send the reply on the next iteration
			reply.onNext(response.build());
			//Then marks it as complete
			reply.onCompleted();
		}
	}

	@Override
	public void addBlock(multiplyBlockRequest request, StreamObserver<multiplyBlockResponse> reply) {
		//If the matricies are not square then the reply informs the user
		if (request.getError().equals("One or both of the matricies are/is not square")) {
			multiplyBlockResponse.Builder response = multiplyBlockResponse.newBuilder();
			reply.onNext(response.build());
			reply.onCompleted();
		}
		//If the matricies are not n^2 such that n is an even number, the reply informs the user
		else if (request.getError().equals("The matracies are not n^2 large")) {
			multiplyBlockResponse.Builder response = multiplyBlockResponse.newBuilder();
			reply.onNext(response.build());
			reply.onCompleted();
		}
		else {
			//Places the matracies from the request data into lists
			List<com.myImplementation.grpc.array> listA = request.getMatrixAList();
			List<com.myImplementation.grpc.array> listB = request.getMatrixBList();

			//Places the List<com.myImplementation.grpc.array> data into a 2D array to make it easier to handle
			int[][] Array1 = listUnpack(listA);
			int[][] Array2 = listUnpack(listB);

			//Adds the matracies
			int[][] addProduct = addBlockAux(Array1, Array2); //addBlockAux

			//Print out a message saying you received a message from the client - for the server to see/display
			System.out.println("Add Request recieved from client:\n" + request);

			//Converts 2D array structure back into the List<com.myImplementation.grpc.array>, since 2D arrays can not be send in requests and responses
			//But List<com.myImplementation.grpc.array> structures can
			multiplyBlockResponse.Builder response = multiplyBlockResponse.newBuilder();
			for (int[] innerArray : addProduct) {
				com.myImplementation.grpc.array.Builder arraySubSet = com.myImplementation.grpc.array.newBuilder();
				arraySubSet.addAllItem(array2List(innerArray));
				response.addMatrixC(arraySubSet.build());
			}
			reply.onNext(response.build());
			reply.onCompleted();
		}
	}

	//Functions used for adding and mutliplying
	//<================>

	// multiplyBlock - provided in lab 1
	static int[][] multiplyBlockAux(int A[][], int B[][]) {
		int MAX = 2;
		int C[][]= new int[MAX][MAX];
		C[0][0]=A[0][0]*B[0][0]+A[0][1]*B[1][0];
		C[0][1]=A[0][0]*B[0][1]+A[0][1]*B[1][1];
		C[1][0]=A[1][0]*B[0][0]+A[1][1]*B[1][0];
		C[1][1]=A[1][0]*B[0][1]+A[1][1]*B[1][1];
		return C;
	}

	//addBlock - provided in lab 1
	static int[][] addBlockAux(int A[][], int B[][]) {
		int MAX = 2;
		int C[][]= new int[MAX][MAX];
		for (int i=0;i<C.length;i++)
		{
			for (int j=0;j<C.length;j++)
			{
				C[i][j]=A[i][j]+B[i][j];
			}
		}
		return C;
	}

	//Extra functions of list packing and uppacking
	//<========================>

	//Converts 1D array into a list of integers
	static List<Integer> array2List(int A[]) {
		List<Integer> listA = new ArrayList<Integer>();
		for(int i = 0; i<(A.length); i++)
		{
			listA.add(A[i]);
		}
		return listA;
	}

	//Converts 2D array into a list of string lists
	static List<List<String>> listRepack(int A[][]) {
		List<List<String>> listA = new ArrayList<List<String>>();
		for(int i = 0; i<(A.length); i++)
		{
			List<String> listB = new ArrayList<String>();
			for(int x = 0; x<(A[i].length); x++)
			{
				listB.add(Integer.toString(A[i][x]));
			}
			listA.add(listB);
		}
		return listA;
	}

	//Converts List<com.myImplementation.grpc.array> stuctures into 2D arrays
	static int[][] listUnpack(List<com.myImplementation.grpc.array> A)  {
		int[][] ArrayA = new int[A.size()][A.get(0).getItemCount()];

		for (int x = 0; x < A.size(); x++)
		{
			for (int y = 0; y < A.get(x).getItemCount(); y++)
			{
				ArrayA[x][y] = A.get(x).getItem(y);
			}
		}

		return ArrayA;
	}

	/*!!NOT USED!!*/
	//Useful Auxilary functions - alternative functions for multiplication and addition
	//<================>
	//Dynamic Multiplication - takes an array of blocks that repesent a matrix broken down into 2x2 blocks and carries out matrix multiplication
	static int[][][] dynamicMatrixMultiplication(int blocksA[][][], int blocksB[][][]) {
		int dim = (int) Math.sqrt(blocksA.length);
		int[][][] blocksOfMultResult = new int[blocksA.length][blocksA[0].length][blocksA[0][0].length];

		for(int i = 0; i < blocksA.length; i++){
			for(int j = 0; j < dim; j++){
				blocksOfMultResult[i] = addBlockAux(blocksOfMultResult[i], multiplyBlockAux(blocksA[j + (dim * (int) Math.floor(i/dim))], blocksB[(j * dim) + (i % dim)]));
			}
		}
		return blocksOfMultResult;
	}

	//Dynamic Addition - takes an array of blocks that repesent a matrix broken down into 2x2 blocks and carries out matrix addition
	static int[][][] dynamicMatrixAddition(int blocksA[][][], int blocksB[][][]) {
		int dim = (int) Math.sqrt(blocksA.length);
		int[][][] blocksOfAdditionResult = new int[blocksA.length][blocksA[0].length][blocksA[0][0].length];

		for(int i = 0; i < blocksA.length; i++){
			blocksOfAdditionResult[i] = addBlockAux(blocksA[i], blocksB[i]);
		}
		return blocksOfAdditionResult;
	}

	//Naive Matrix adding
	static int[][] addBlockAux2(int A[][], int B[][]) {
		//Result will be stored in this array - it will be the same dimentions as A and B and will be square
		int[][] C = new int[A.length][A.length];

		//Loops through each element in both matricies/arrays, adds them and stores the results in the new array C
		for (int i = 0; i < A.length; i++)
		{
			for (int j = 0; j < A.length; j++)
			{
				C[i][j] = A[i][j]+B[i][j];
			}
		}

		return C;
	}

	//Naive Multiplying
	static int[][] multiplyBlockAux2(int A[][], int B[][]) {
		//Stores results
		int[][] C = new int[A.length][A.length];

		for (int a = 0; a < A.length; a++) {
			for (int b = 0; b < A.length; b++) {
				for (int c = 0; c < A.length; c++) {
					//Goes through array A column-wise and goes through B row-wise and multiplies the results them adds the adds ther result to revious calculations
					C[a][b] += A[a][c] * B[c][b];
				}
			}
		}
		return C;
	}

	//multiplyMatrixBlockAux - provided in lab 1
	static int[][] multiplyMatrixBlockAux(int A[][], int B[][]) {
		int MAX = 4;
		int bSize = 2;
		int[][] A1 = new int[MAX][MAX];
		int[][] A2 = new int[MAX][MAX];
		int[][] A3 = new int[MAX][MAX];
		int[][] B1 = new int[MAX][MAX];
		int[][] B2 = new int[MAX][MAX];
		int[][] B3 = new int[MAX][MAX];
		int[][] C1 = new int[MAX][MAX];
		int[][] C2 = new int[MAX][MAX];
		int[][] C3 = new int[MAX][MAX];
		int[][] D1 = new int[MAX][MAX];
		int[][] D2 = new int[MAX][MAX];
		int[][] D3 = new int[MAX][MAX];
		int[][] res= new int[MAX][MAX];
		for (int i = 0; i < bSize; i++)
		{
			for (int j = 0; j < bSize; j++)
			{
				A1[i][j]=A[i][j];
				A2[i][j]=B[i][j];
			}
		}
		for (int i = 0; i < bSize; i++)
		{
			for (int j = bSize; j < MAX; j++)
			{
				B1[i][j-bSize]=A[i][j];
				B2[i][j-bSize]=B[i][j];
			}
		}
		for (int i = bSize; i < MAX; i++)
		{
			for (int j = 0; j < bSize; j++)
			{
				C1[i-bSize][j]=A[i][j];
				C2[i-bSize][j]=B[i][j];
			}
		}
		for (int i = bSize; i < MAX; i++)
		{
			for (int j = bSize; j < MAX; j++)
			{
				D1[i-bSize][j-bSize]=A[i][j];
				D2[i-bSize][j-bSize]=B[i][j];
			}
		}
		A3=addBlockAux(multiplyBlockAux(A1,A2),multiplyBlockAux(B1,C2));
		B3=addBlockAux(multiplyBlockAux(A1,B2),multiplyBlockAux(B1,D2));
		C3=addBlockAux(multiplyBlockAux(C1,A2),multiplyBlockAux(D1,C2));
		D3=addBlockAux(multiplyBlockAux(C1,B2),multiplyBlockAux(D1,D2));
		for (int i = 0; i < bSize; i++)
		{
			for (int j = 0; j < bSize; j++)
			{
				res[i][j]=A3[i][j];
			}
		}
		for (int i = 0; i < bSize; i++)
		{
			for (int j = bSize; j < MAX; j++)
			{
				res[i][j]=B3[i][j-bSize];
			}
		}
		for (int i = bSize; i < MAX; i++)
		{
			for (int j = 0; j < bSize; j++)
			{
				res[i][j]=C3[i-bSize][j];
			}
		}
		for (int i = bSize; i < MAX; i++)
		{
			for (int j = bSize; j < MAX; j++)
			{
				res[i][j]=D3[i-bSize][j-bSize];
			}
		}
		for (int i=0; i<MAX; i++)
		{
			for (int j=0; j<MAX;j++)
			{
				System.out.print(res[i][j]+" ");
			}
			System.out.println("");
		}
		return res;
	}
}

/*
//Code used for guidance - ignore

@GrpcService
//In PingPongService.proto there is the service "PingPongService"
public class PingPongServiceImpl extends PingPongServiceGrpc.PingPongServiceImplBase { //PingPongServiceImplBase is generated from the proto file and deals with basic network communication

    @Override //We override the ping function declared in proto
	//Ping is a rpc function in the proto (within the PingPongService service)
    public void ping(PingRequest request, StreamObserver<PongResponse> responseObserver) { //issue with using uppercase as first word for method name
    	//Proto has a message called PingRequest
		//StreamObserver<PongResponse> "responseObserver" lets you do async communication if you want

		//Makes a string, "ping", with the word "Pong"
		String ping = new StringBuilder()
                .append("pong")
                .toString();

		//Builds a response constainer - setting "pong" (a message varaible in the proto's "PongResponse" message) to the value of the string Ping ("Pong")
		PongResponse response = PongResponse.newBuilder()
                .setPong(ping)
                .build();  //.build builds the response with the info we provided

		//specifies we want to send it on the next iteration
		responseObserver.onNext(response);
		//Then marks it as complete
		responseObserver.onCompleted();
    }
}
 */
