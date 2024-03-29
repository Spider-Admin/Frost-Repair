package org.frost.repair;
/*
  Copyright 2021 - 2023 Spider-Admin@Z+d9Knmjd3hQeeZU6BOWPpAAxxs

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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

import org.garret.perst.AssertionFailed;
import org.garret.perst.IPersistentList;
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
import frost.storage.perst.messages.PerstBoardAttachment;
import frost.storage.perst.messages.PerstFileAttachment;
import frost.storage.perst.messages.PerstFrostBoardObject;
import frost.storage.perst.messages.PerstFrostMessageObject;

public class FrostRepair {

	private static final Logger log = LoggerFactory.getLogger(FrostRepair.class);

	private static final String STORE_PATH = "store";

	private static final Charset CHARSET = StandardCharsets.UTF_8;

	private static final String MESSAGE_FILE = "messages.dbs";
	private static final String MESSAGE_CONTENT_FILE = "messagesContents.dbs";

	private static final String PERST_ENCODING = "perst.string.encoding";

	private String getTempDir() {
		return System.getProperty("java.io.tmpdir");
	}

	private void copyFileToTemp(Path source) throws IOException {
		String tempDir = getTempDir();

		Path destination = Paths.get(tempDir, source.getFileName().toString());
		destination.toFile().deleteOnExit();

		log.info("Copy {} to temporary folder...", source.getFileName());
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

		String tempDir = getTempDir();

		copyFileToTemp(Paths.get(path, STORE_PATH, MESSAGE_FILE));
		copyFileToTemp(Paths.get(path, STORE_PATH, MESSAGE_CONTENT_FILE));

		repairMessages(Paths.get(tempDir, MESSAGE_FILE), Paths.get(tempDir, MESSAGE_CONTENT_FILE),
				Paths.get(path, STORE_PATH, MESSAGE_CONTENT_FILE + ".repaired"));
	}

	private Boolean isKnownError(Throwable e) {
		if (e instanceof ClassCastException) {
			return true;
		} else if (e instanceof AssertionFailed) {
			return true;
		} else if (e instanceof ArrayIndexOutOfBoundsException) {
			return true;
		} else if (e instanceof StorageError) {
			Integer errorCode = ((StorageError) e).getErrorCode();
			if (errorCode == StorageError.DELETED_OBJECT || errorCode == StorageError.INVALID_OID
					|| errorCode == StorageError.FILE_ACCESS_ERROR) {
				return true;
			}
		}
		return false;
	}

	private Integer repairMessagesFromList(MessageContentStorageRoot oldMessageContentRoot,
			MessageContentStorageRoot newMessageContentRoot, Storage newMessageContentStorage,
			Iterator<PerstFrostMessageObject> messageIt) {
		Integer messageCount = 0;
		Index<PerstString> messageContents = oldMessageContentRoot.getContentByMsgOid();
		Index<PerstString> publicKeys = oldMessageContentRoot.getPublickeyByMsgOid();
		Index<PerstString> signatures = oldMessageContentRoot.getSignatureByMsgOid();
		Index<PerstAttachments> attachments = oldMessageContentRoot.getAttachmentsByMsgOid();

		Index<PerstString> messageContentsNew = newMessageContentRoot.getContentByMsgOid();
		Index<PerstString> publicKeysNew = newMessageContentRoot.getPublickeyByMsgOid();
		Index<PerstString> signaturesNew = newMessageContentRoot.getSignatureByMsgOid();
		Index<PerstAttachments> attachmentsNew = newMessageContentRoot.getAttachmentsByMsgOid();

		while (messageIt.hasNext()) {
			PerstFrostMessageObject message = messageIt.next();
			int oid = message.getOid();
			messageCount = messageCount + 1;

			PerstString messageContent = null;
			try {
				messageContent = messageContents.get(oid);
			} catch (ClassCastException | AssertionFailed | ArrayIndexOutOfBoundsException | StorageError e) {
				if (isKnownError(e)) {
					log.warn("Remove broken message (OID = {})", oid);
				} else {
					throw e;
				}
			}

			PerstString publicKey = null;
			try {
				publicKey = publicKeys.get(oid);
			} catch (ClassCastException | AssertionFailed | ArrayIndexOutOfBoundsException | StorageError e) {
				if (isKnownError(e)) {
					log.warn("Remove broken public key of message (OID = {})", oid);
				} else {
					throw e;
				}
			}

			PerstString signature = null;
			try {
				signature = signatures.get(oid);
			} catch (ClassCastException | AssertionFailed | ArrayIndexOutOfBoundsException | StorageError e) {
				if (isKnownError(e)) {
					log.warn("Remove broken signature of message (OID = {})", oid);
				} else {
					throw e;
				}
			}

			IPersistentList<PerstBoardAttachment> boardAttachments = null;
			IPersistentList<PerstFileAttachment> fileAttachments = null;
			try {
				PerstAttachments attachment = attachments.get(oid);
				if (attachment != null) {
					try {
						boardAttachments = attachment.getBoardAttachments();
						if (boardAttachments != null) {
							Iterator<PerstBoardAttachment> attachmentIt = boardAttachments.iterator();
							while (attachmentIt.hasNext()) {
								attachmentIt.next();
							}
						}
					} catch (ClassCastException | AssertionFailed | ArrayIndexOutOfBoundsException | StorageError e) {
						if (isKnownError(e)) {
							boardAttachments = null;
							log.warn("Remove broken board attachments of message (OID = {})", oid);
						} else {
							throw e;
						}
					}
					try {
						fileAttachments = attachment.getFileAttachments();
						if (fileAttachments != null) {
							Iterator<PerstFileAttachment> attachmentIt = fileAttachments.iterator();
							while (attachmentIt.hasNext()) {
								attachmentIt.next();
							}
						}
					} catch (ClassCastException | AssertionFailed | ArrayIndexOutOfBoundsException | StorageError e) {
						if (isKnownError(e)) {
							fileAttachments = null;
							log.warn("Remove broken file attachments of message (OID = {})", oid);
						} else {
							throw e;
						}
					}
				}
			} catch (ClassCastException | AssertionFailed | ArrayIndexOutOfBoundsException | StorageError e) {
				if (isKnownError(e)) {
					log.warn("Remove broken attachments of message (OID = {})", oid);
				} else {
					throw e;
				}
			}

			if (messageContent != null) {
				messageContentsNew.put(oid, new PerstString(messageContent));
			} else {
				messageContentsNew.put(oid, new PerstString(""));
			}

			if (publicKey != null) {
				publicKeysNew.put(oid, new PerstString(publicKey));
			} else {
				// Frost does not store missing publicKey,
				// because Perst can't store null-values!
				// publicKeysNew.put(oid, null);
			}

			if (signature != null) {
				signaturesNew.put(oid, new PerstString(signature));
			} else {
				// Frost does not store missing signature,
				// because Perst can't store null-values!
				// signaturesNew.put(oid, null);
			}

			attachmentsNew.put(oid, new PerstAttachments(newMessageContentStorage, boardAttachments, fileAttachments));

			if (messageCount % 10000 == 0) {
				newMessageContentStorage.commit();
			}
		}
		return messageCount;
	}

	private void repairMessages(Path filenameMessages, Path filenameMessageContents, Path newFilenameMessageContents)
			throws IOException {
		log.info("Load messages from {} and {}", filenameMessages, filenameMessageContents);

		Storage dbMessages = openStorage(filenameMessages.toString());
		MessageStorageRoot rootMessages = (MessageStorageRoot) dbMessages.getRoot();
		if (rootMessages == null) {
			throw new IOException(String.format("\"%s\" contains no data!", filenameMessages));
		}

		Storage dbMessageContents = openStorage(filenameMessageContents.toString());
		MessageContentStorageRoot rootMessageContents = (MessageContentStorageRoot) dbMessageContents.getRoot();
		if (rootMessageContents == null) {
			throw new IOException(String.format("\"%s\" contains no data!", filenameMessageContents));
		}

		log.info("Creating new dbs-file {}", newFilenameMessageContents);
		Files.deleteIfExists(newFilenameMessageContents);
		Storage dbMessageContentsNew = openStorage(newFilenameMessageContents.toString());
		MessageContentStorageRoot rootMessageContentsNew = (MessageContentStorageRoot) dbMessageContentsNew.getRoot();
		if (rootMessageContentsNew == null) {
			rootMessageContentsNew = new MessageContentStorageRoot(dbMessageContentsNew);
			dbMessageContentsNew.setRoot(rootMessageContentsNew);
			dbMessageContentsNew.commit();
		}

		Index<PerstFrostBoardObject> boards = rootMessages.getBoardsByName();
		Iterator<PerstFrostBoardObject> boardIt = boards.iterator();

		while (boardIt.hasNext()) {
			PerstFrostBoardObject board = boardIt.next();
			log.info("Copy message-contents from board {} ...", board.getBoardName());

			// @see frost.storage.perst.messages.MessageStorage.insertMessage(...)
			// getMessageIndex() = All valid messages
			// getUnreadMessageIndex() = Subset of getMessageIndex()
			// getFlaggedMessageIndex() = Subset of getMessageIndex()
			// getStarredMessageIndex() = Subset of getMessageIndex()
			// getMessageIdIndex() = Subset of getMessageIndex()
			// getInvalidMessagesIndex() = All invalid messages, never shown in GUI
			// getSentMessagesList() = All sent messages, OID differs

			Integer messageCount = repairMessagesFromList(rootMessageContents, rootMessageContentsNew,
					dbMessageContentsNew, board.getMessageIndex().iterator());
			log.debug("getMessageIndex = {}", messageCount);

			messageCount = repairMessagesFromList(rootMessageContents, rootMessageContentsNew, dbMessageContentsNew,
					board.getInvalidMessagesIndex().iterator());
			log.debug("getInvalidMessagesIndex = {}", messageCount);

			messageCount = repairMessagesFromList(rootMessageContents, rootMessageContentsNew, dbMessageContentsNew,
					board.getSentMessagesList().iterator());
			log.debug("getSentMessagesList = {}", messageCount);

			// @see frost.storage.perst.messages.PerstFrostUnsentMessageObject
			// getUnsentMessagesList() = All unsent messages, stored in MESSAGE_FILE
			// getDraftMessagesList() = Not used in Frost, stored in MESSAGE_FILE

			dbMessageContentsNew.commit();
		}
		dbMessages.close();
		dbMessageContents.close();
		dbMessageContentsNew.close();

		log.info("Saved repaired dbs-file {}", newFilenameMessageContents);
	}
}