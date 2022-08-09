package org.frost.repair;
/*
  Copyright 2021 Spider-Admin@Z+d9Knmjd3hQeeZU6BOWPpAAxxs

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

import org.garret.perst.Index;
import org.garret.perst.Storage;
import org.garret.perst.StorageError;
import org.garret.perst.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import frost.storage.perst.PerstString;
import frost.storage.perst.messages.MessageContentStorageRoot;
import frost.storage.perst.messages.MessageStorageRoot;
import frost.storage.perst.messages.PerstAttachments;
import frost.storage.perst.messages.PerstFrostBoardObject;
import frost.storage.perst.messages.PerstFrostMessageObject;

public class FrostRepair {

	private static final Logger log = LoggerFactory.getLogger(FrostRepair.class);

	private static final String STORE_PATH = "store";

	private static final Charset CHARSET = StandardCharsets.UTF_8;

	private static final String MESSAGE_FILE = "messages.dbs";
	private static final String MESSAGE_CONTENT_FILE = "messagesContents.dbs";

	private static final String PERST_ENCODING = "perst.string.encoding";

	private void copyFileToTemp(String filename) throws IOException {
		String tempDir = System.getProperty("java.io.tmpdir");

		Path source = Paths.get(filename);
		Path destination = Paths.get(tempDir + source.getFileName());
		destination.toFile().deleteOnExit();

		log.info("Copy {} to temporary folder...", new File(filename).getName());
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private Storage openStorage(String filename) {
		Storage storage = StorageFactory.getInstance().createStorage();
		storage.setProperty(PERST_ENCODING, CHARSET.name());
		storage.open(filename, Storage.DEFAULT_PAGE_POOL_SIZE);
		return storage;
	}

	public void repair(String path) throws IOException {
		log.info("Repair dbs-files ...");

		if (!path.substring(path.length() - 1).equals(File.separator)) {
			path = path + File.separator;
		}

		String tempDir = System.getProperty("java.io.tmpdir");

		copyFileToTemp(path + STORE_PATH + File.separator + MESSAGE_FILE);
		copyFileToTemp(path + STORE_PATH + File.separator + MESSAGE_CONTENT_FILE);

		String newFilenameMessageContents = path + STORE_PATH + File.separator + MESSAGE_CONTENT_FILE + ".repaired";

		repairMessages(tempDir + MESSAGE_FILE, tempDir + MESSAGE_CONTENT_FILE, newFilenameMessageContents);
	}

	private void repairMessages(String filenameMessages, String filenameMessageContents,
			String newFilenameMessageContents) throws IOException {
		log.info("Load messages from {} and {}", filenameMessages, filenameMessageContents);

		Storage dbMessages = openStorage(filenameMessages);
		MessageStorageRoot rootMessages = (MessageStorageRoot) dbMessages.getRoot();
		if (rootMessages == null) {
			throw new IOException(String.format("\"%s\" contains no data!", filenameMessages));
		}

		Storage dbMessageContents = openStorage(filenameMessageContents);
		MessageContentStorageRoot rootMessageContents = (MessageContentStorageRoot) dbMessageContents.getRoot();
		if (rootMessageContents == null) {
			throw new IOException(String.format("\"%s\" contains no data!", filenameMessageContents));
		}

		log.info("Creating new dbs-file {}", newFilenameMessageContents);
		Files.deleteIfExists(Paths.get(newFilenameMessageContents));
		Storage dbMessageContentsNew = openStorage(newFilenameMessageContents);
		MessageContentStorageRoot rootMessageContentsNew = (MessageContentStorageRoot) dbMessageContentsNew.getRoot();
		if (rootMessageContentsNew == null) {
			rootMessageContentsNew = new MessageContentStorageRoot(dbMessageContentsNew);
			dbMessageContentsNew.setRoot(rootMessageContentsNew);
			dbMessageContentsNew.commit();
		}

		Index<PerstString> messageContents = rootMessageContents.getContentByMsgOid();
		Index<PerstString> publicKeys = rootMessageContents.getPublickeyByMsgOid();
		Index<PerstString> signatures = rootMessageContents.getSignatureByMsgOid();
		Index<PerstAttachments> attachments = rootMessageContents.getAttachmentsByMsgOid();

		Index<PerstString> messageContentsNew = rootMessageContentsNew.getContentByMsgOid();
		Index<PerstString> publicKeysNew = rootMessageContentsNew.getPublickeyByMsgOid();
		Index<PerstString> signaturesNew = rootMessageContentsNew.getSignatureByMsgOid();
		Index<PerstAttachments> attachmentsNew = rootMessageContentsNew.getAttachmentsByMsgOid();

		Index<PerstFrostBoardObject> boards = rootMessages.getBoardsByName();
		Iterator<PerstFrostBoardObject> boardIt = boards.iterator();

		Integer messageCount = 0;
		while (boardIt.hasNext()) {
			PerstFrostBoardObject board = boardIt.next();
			log.info("Copy message-contents from board {} ...", board.getBoardName());

			Index<PerstFrostMessageObject> messages = board.getMessageIndex();

			Iterator<PerstFrostMessageObject> messageIt = messages.iterator();
			while (messageIt.hasNext()) {
				PerstFrostMessageObject message = messageIt.next();
				int oid = message.getOid();
				try {
					messageCount = messageCount + 1;
					PerstString messageContent = messageContents.get(oid);
					PerstString publicKey = publicKeys.get(oid);
					PerstString signature = signatures.get(oid);
					PerstAttachments attachment = attachments.get(oid);

					messageContentsNew.put(oid, new PerstString(messageContent));
					if (publicKey != null) {
						publicKeysNew.put(oid, new PerstString(publicKey));
					}
					if (signature != null) {
						signaturesNew.put(oid, new PerstString(signature));
					}
					attachmentsNew.put(oid, new PerstAttachments(dbMessageContentsNew, attachment.getBoardAttachments(),
							attachment.getFileAttachments()));

					if (messageCount % 10000 == 0) {
						dbMessageContentsNew.commit();
					}
				} catch (StorageError e) {
					if (e.getErrorCode() == StorageError.DELETED_OBJECT) {
						log.warn("Replace broken message (OID={}) with dummy", oid);

						// Frost does not store missing publicKey/signature,
						// because Perst can't store null-values!
						messageContentsNew.put(oid, new PerstString(""));
						// publicKeysNew.put(oid, null);
						// signaturesNew.put(oid, null);
						attachmentsNew.put(oid, new PerstAttachments(dbMessageContentsNew, null, null));
						dbMessageContentsNew.commit();
					} else {
						throw e;
					}
				}
			}
		}
		dbMessageContentsNew.commit();

		dbMessages.close();
		dbMessageContents.close();
		dbMessageContentsNew.close();
	}
}
