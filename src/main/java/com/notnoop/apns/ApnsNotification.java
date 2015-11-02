/*
 * Copyright 2010, Mahmood Ali.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Mahmood Ali. nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.notnoop.apns;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import com.notnoop.apns.internal.Utilities;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represents an APNS notification to be sent to Apple service.
 */
public class ApnsNotification {

	public final static byte COMMAND = 2;
	private static AtomicInteger nextId = new AtomicInteger(0);
	private final int identifier;
	private final int expiry;
	private final byte[] deviceToken;
	private final byte[] payload;

	private final Priority priority;

	public static enum Priority {
		SEND_IMMEDIATELY((byte) 10), SEND_AT_CONVENIENCE((byte) 5);
		private byte value;

		private Priority(byte value) {
			this.value = value;
		}

		public byte getByteValue() {
			return value;
		}
	}

	public static enum FrameId {
		DEVICE_TOKEN((byte) 1), PAYLOAD((byte) 2), NOTIFICATION_ID((byte) 3), EXPIRATION_DATE(
				(byte) 4), PRIORITY((byte) 5);
		private byte value;

		private FrameId(byte value) {
			this.value = value;
		}

		public byte getByteValue() {
			return value;
		}
	}

	public static int INCREMENT_ID() {
		return nextId.incrementAndGet();
	}

	/**
	 * The infinite future for the purposes of Apple expiry date
	 */
	public final static int MAXIMUM_EXPIRY = Integer.MAX_VALUE;

	/**
	 * Constructs an instance of {@code ApnsNotification}.
	 *
	 * The message encodes the payload with a {@code UTF-8} encoding.
	 *
	 * @param dtoken
	 *            The Hex of the device token of the destination phone
	 * @param payload
	 *            The payload message to be sent
	 */
	public ApnsNotification(int identifier, int expiryTime, String dtoken,
			String payload, Priority priority) {
		this.identifier = identifier;
		this.expiry = expiryTime;
		this.deviceToken = Utilities.decodeHex(dtoken);
		this.payload = Utilities.toUTF8Bytes(payload);
		this.priority = priority;
	}

	/**
	 * Constructs an instance of {@code ApnsNotification}.
	 *
	 * @param dtoken
	 *            The binary representation of the destination device token
	 * @param payload
	 *            The binary representation of the payload to be sent
	 */
	public ApnsNotification(int identifier, int expiryTime, byte[] dtoken,
			byte[] payload, Priority priority) {
		this.identifier = identifier;
		this.expiry = expiryTime;
		this.deviceToken = Utilities.copyOf(dtoken);
		this.payload = Utilities.copyOf(payload);
		this.priority = priority;
	}

	/**
	 * Returns the binary representation of the device token.
	 *
	 */
	public byte[] getDeviceToken() {
		return Utilities.copyOf(deviceToken);
	}

	/**
	 * Returns the binary representation of the payload.
	 *
	 */
	public byte[] getPayload() {
		return Utilities.copyOf(payload);
	}

	public int getIdentifier() {
		return identifier;
	}

	public int getExpiry() {
		return expiry;
	}

	public Priority getPriority() {
		return priority;
	}

	private byte[] marshalFrameData() {
		final ByteArrayOutputStream boas = new ByteArrayOutputStream();
		final DataOutputStream dos = new DataOutputStream(boas);

		try {
			// Device token
			dos.writeByte(FrameId.DEVICE_TOKEN.getByteValue());
			dos.writeShort(32);
			dos.write(deviceToken);

			dos.writeByte(FrameId.PAYLOAD.getByteValue());
			dos.writeShort(payload.length);
			dos.write(payload);

			dos.writeByte(FrameId.NOTIFICATION_ID.getByteValue());
			dos.writeShort(4);
			dos.writeInt(identifier);

			dos.writeByte(FrameId.EXPIRATION_DATE.getByteValue());
			dos.writeShort(4);
			dos.writeInt(expiry);

			dos.writeByte(FrameId.PRIORITY.getByteValue());
			dos.writeShort(1);
			dos.writeByte(priority.getByteValue());
			return boas.toByteArray();
		} catch (final IOException e) {
			throw new AssertionError("Should not happen but happended: ", e);
		} finally {
			closeStreamSilently(boas);
			closeStreamSilently(dos);
		}
	}

	private static void closeStreamSilently(java.io.Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException e) {

		}

	}

	/**
	 * Returns the binary representation of the message as expected by the APNS
	 * server.
	 *
	 * The returned array can be used to sent directly to the APNS server (on
	 * the wire/socket) without any modification.
	 */
	public byte[] marshall() {

		byte[] frameData = marshalFrameData();

		final ByteArrayOutputStream boas = new ByteArrayOutputStream();
		final DataOutputStream dos = new DataOutputStream(boas);

		try {
			dos.writeByte(COMMAND);
			dos.writeInt(frameData.length);
			dos.write(frameData);
			return boas.toByteArray();
		} catch (final IOException e) {
			throw new AssertionError("Should not happen but happended: ", e);
		} finally {
			closeStreamSilently(boas);
			closeStreamSilently(dos);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(deviceToken);
		result = prime * result + expiry;
		result = prime * result + identifier;
		result = prime * result + Arrays.hashCode(payload);
		result = prime * result
				+ ((priority == null) ? 0 : priority.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ApnsNotification other = (ApnsNotification) obj;
		if (!Arrays.equals(deviceToken, other.deviceToken))
			return false;
		if (expiry != other.expiry)
			return false;
		if (identifier != other.identifier)
			return false;
		if (!Arrays.equals(payload, other.payload))
			return false;
		if (priority != other.priority)
			return false;
		return true;
	}

	@Override
	@SuppressFBWarnings("DE_MIGHT_IGNORE")
	public String toString() {
		String payloadString;
		try {
			payloadString = new String(payload, "UTF-8");
		} catch (Exception ex) {
			payloadString = "???";
		}
		return "Message(Id=" + identifier + "; Token="
				+ Utilities.encodeHex(deviceToken) + "; Payload="
				+ payloadString + ")";
	}

	//for testing
	public int length() {
		return marshall().length;
	}
}
