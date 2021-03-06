package merkleClient;

import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MerkleValidityRequest {

	/**
	 * IP address of the authority
	 * */
	private final String authIPAddr;
	/**
	 * Port number of the authority
	 * */
	private final int  authPort;
	/**
	 * Hash value of the merkle tree root. 
	 * Known before-hand.
	 * */
	private final String mRoot;
	/**
	 * List of transactions this client wants to verify 
	 * the existence of.
	 * */
	private List<String> mRequests;
	
	/**
	 * Sole constructor of this class - marked private.
	 * */
	private MerkleValidityRequest(Builder b){
		this.authIPAddr = b.authIPAddr;
		this.authPort = b.authPort;
		this.mRoot = b.mRoot;
		this.mRequests = b.mRequest;
	}
	
	/**
	 * <p>Method implementing the communication protocol between the client and the authority.</p>
	 * <p>The steps involved are as follows:</p>
	 * 		<p>0. Opens a connection with the authority</p>
	 * 	<p>For each transaction the client does the following:</p>
	 * 		<p>1.: asks for a validityProof for the current transaction</p>
	 * 		<p>2.: listens for a list of hashes which constitute the merkle nodes contents</p>
	 * 	<p>Uses the utility method {@link #isTransactionValid(String, String, List<String>) isTransactionValid} </p>
	 * 	<p>method to check whether the current transaction is valid or not.</p>
	 * */

	public Map<Boolean, List<String>> checkWhichTransactionValid() throws IOException {
		HashMap<Boolean, List<String>> mapResults = new HashMap<>();
		ArrayList<String> invalidTrans = new ArrayList<>();
		ArrayList<String> validTrans = new ArrayList<>();

		/**
		 * Open connection with the server
		 */
		InetSocketAddress serverAdr = new InetSocketAddress(authIPAddr, authPort);
		try {
			SocketChannel socket = SocketChannel.open(serverAdr);
			socket.configureBlocking(true);
			/**
			 * I used and arbitrary size of the buffer we can receive 64 nodes
			 */
			ByteBuffer buffer = ByteBuffer.allocate(32);

			/**
			 * For each request write the channel using the buffer. When the server receive the request, it responds
			 * with a list of complementary nodes of the merkle tree. The list is written in a buffer chaining all the string of the nodes.
			 */
			for (String singleRequest : mRequests) {
				byte[] message = singleRequest.getBytes();
				buffer.clear();
				buffer.put(ByteBuffer.wrap(message));
				buffer.flip();
				System.out.println("Sending: " + singleRequest);
				socket.write(buffer);



				//receiving header in which is written the size of the data buffer to allocate
				ByteBuffer header = ByteBuffer.allocate(4);
				socket.read(header);
				int bufferSize = header.getInt(0);
				System.out.println("Buffer size received: "+bufferSize);

				//Creating a ByteBuffer in which are stored the data sent by the server
				ByteBuffer out = ByteBuffer.allocate(bufferSize);
				socket.read(out);

				ArrayList<String> list = new ArrayList<>();
				out.flip();
				/**
				 * Assuming that every complementary node contains the hash of the chaining of the hash its children we know
				 * that string is 32 chars long, so we read chunks of 32 bytes every cycle, in this way we can reconstruct the
				 * list sent by the server.
				 */
				int i = 0;
				while (out.hasRemaining()) {
					byte[] tmp = new byte[32];
					int off = 32 * i;
					int j = 0;
					for (int z = off; z < off + 32; z++) {
						tmp[j] = out.get();
						j++;
					}
					++i;

					String msg = new String(tmp, "UTF-8");
					list.add(msg);
				}

				if (isTransactionValid(singleRequest, list) == true)
					validTrans.add(singleRequest);
				else
					invalidTrans.add(singleRequest);
			}
			/**
			 * Close connection
			 */
			String close = "close";

			byte[] closeConnection = close.getBytes();

			buffer.clear();
			buffer.put(closeConnection);
			buffer.flip();
			socket.write(buffer);

			socket.close();

			mapResults.put(true, validTrans);
			mapResults.put(false, invalidTrans);
			return mapResults;

		} catch (ConnectException e) {
			System.out.println("Connection refused, returning empty map");
			e.printStackTrace();
			return mapResults;
		}
	}

	/**
	 * 	Checks whether a transaction 'merkleTx' is part of the merkle tree.
	 *
	 *  @param merkleTx String: the transaction we want to validate
	 *  @param merkleNodes String: the hash codes of the merkle nodes required to compute
	 *  the merkle root
	 *
	 *  @return: boolean value indicating whether this transaction was validated or not.
	 * */
	private boolean isTransactionValid(String merkleTx, List<String> merkleNodes) {
		String computedRoot = merkleTx;
		for (String node : merkleNodes) {
			computedRoot = HashUtil.md5Java(computedRoot + node);
		}
		return computedRoot == mRoot;
	}


	/**
	 * Builder for the MerkleValidityRequest class. 
	 * */
	public static class Builder {
		private String authIPAddr;
		private int authPort;
		private String mRoot;
		private List<String> mRequest;	
		
		public Builder(String authorityIPAddr, int authorityPort, String merkleRoot) {
			this.authIPAddr = authorityIPAddr;
			this.authPort = authorityPort;
			this.mRoot = merkleRoot;
			mRequest = new ArrayList<>();
		}
				
		public Builder addMerkleValidityCheck(String merkleHash) {
			mRequest.add(merkleHash);
			return this;
		}
		
		public MerkleValidityRequest build() {
			return new MerkleValidityRequest(this);
		}
	}
}