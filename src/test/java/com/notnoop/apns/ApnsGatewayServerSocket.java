package com.notnoop.apns;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;

import com.notnoop.apns.ApnsNotification.FrameId;
import com.notnoop.apns.ApnsNotification.Priority;

/**
 * Represents the Apple APNS server. This allows testing outside of the Apple
 * servers.
 */
public class ApnsGatewayServerSocket extends AbstractApnsServerSocket {
	private final ApnsServerService apnsServerService;

	public ApnsGatewayServerSocket(SSLContext sslContext, int port,
			ExecutorService executorService,
			ApnsServerService apnsServerService,
			ApnsServerExceptionDelegate exceptionDelegate) throws IOException {
		super(sslContext, port, executorService, exceptionDelegate);
		this.apnsServerService = apnsServerService;
	}

	public static int byteArrayToInt(byte[] b, int offset) {
		int value = 0;
		for (int i = 0; i < 4; i++) {
			int shift = (4 - 1 - i) * 8;
			value += (b[i + offset] & 0x000000FF) << shift;
		}
		return value;
	}

	@Override
	void handleSocket(Socket socket) throws IOException {
		InputStream inputStream = socket.getInputStream();
		DataInputStream dataInputStream = new DataInputStream(inputStream);
		while (true) {
			int command;

			int frameBytesLeft = 0;

			int frameId;
			short frameLength;
			byte[] frameData;
			int identifier = 0;

			try {
				command = dataInputStream.read();
				frameBytesLeft = dataInputStream.readInt();
				byte[] deviceTokenBytes = null;
				byte[] payloadBytes = null;
				int expiry = 0;
				Priority priority = null;
				while (frameBytesLeft > 0) {
					frameId = dataInputStream.read();
					frameLength = dataInputStream.readShort();
					frameData = toArray(inputStream, frameLength);

					frameBytesLeft = frameBytesLeft - 1 - 2 - frameLength;
					if (frameId == FrameId.DEVICE_TOKEN.getByteValue()) {
						deviceTokenBytes = frameData;
					} else if (frameId == FrameId.PAYLOAD.getByteValue()) {
						payloadBytes = frameData;
					} else if (frameId == FrameId.EXPIRATION_DATE
							.getByteValue()) {
						expiry = byteArrayToInt(frameData, 0);
					} else if (frameId == FrameId.PRIORITY.getByteValue()) {
						if (frameData[0] == Priority.SEND_AT_CONVENIENCE
								.getByteValue()) {
							priority = Priority.SEND_AT_CONVENIENCE;
						} else {
							priority = Priority.SEND_IMMEDIATELY;
						}
					} else if (frameId == FrameId.NOTIFICATION_ID
							.getByteValue()) {
						identifier = byteArrayToInt(frameData, 0);
					}
				}

				if(command!=ApnsNotification.COMMAND) {
					writeResponse(socket, identifier, 8, 1);
					break;
				}
				ApnsNotification message;
				message = new ApnsNotification(identifier, expiry,
						deviceTokenBytes, payloadBytes, priority);
				apnsServerService.messageReceived(message);
			} catch (IOException ioe) {
				writeResponse(socket, identifier, 8, 1);
				break;
			} catch (Exception e) {
				writeResponse(socket, identifier, 8, 1);
				break;
			}
		}
		
		System.out.println("Ended");
	}

	private void writeResponse(Socket socket, int identifier, int command,
			int status) {
		try {
			BufferedOutputStream bos = new BufferedOutputStream(
					socket.getOutputStream());
			DataOutputStream dataOutputStream = new DataOutputStream(bos);
			dataOutputStream.writeByte(command);
			dataOutputStream.writeByte(status);
			dataOutputStream.writeInt(identifier);
			dataOutputStream.flush();
		} catch (IOException ioe) {
			// if we can't write a response, nothing we can do
		}
	}

	private byte[] toArray(InputStream inputStream, int size)
			throws IOException {
		byte[] bytes = new byte[size];
		final DataInputStream dis = new DataInputStream(inputStream);
		dis.readFully(bytes);
		return bytes;
	}
}