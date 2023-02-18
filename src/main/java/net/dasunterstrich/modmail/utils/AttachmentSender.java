package net.dasunterstrich.modmail.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AttachmentSender {
    private static final long MAX_FILE_SIZE = 8 << 20;
    private static final Logger logger = LoggerFactory.getLogger(AttachmentSender.class);
    private static final ExecutorService executorService = Executors.newScheduledThreadPool(2);

    public static void sendAttachment(MessageChannel channel, List<Message.Attachment> attachments, Consumer<Boolean> success, Consumer<Void> fileTooBig) {
        executorService.submit(() -> {
            var fileUploads = new HashSet<FileUpload>();

            try {
                for (var attachment : attachments) {
                    try {
                        var inputStream = attachment.getProxy().download().get();
                        if (inputStream.readAllBytes().length > MAX_FILE_SIZE) {
                            logger.warn("Too big file uploaded in " + channel.getName());
                            continue;
                        }

                        fileUploads.add(FileUpload.fromData(attachment.getProxy().download().get(), attachment.getFileName()));

                    } catch (InterruptedException | ExecutionException | IOException exception) {
                        logger.error(String.valueOf(exception));
                        success.accept(false);
                    }
                }

                if (!fileUploads.isEmpty()) {
                    channel.sendFiles(fileUploads).queue(message -> success.accept(true), throwable -> {
                        logger.error("Error", throwable);
                        success.accept(false);
                    });
                } else {
                    fileTooBig.accept(null);
                    success.accept(false);
                    return;
                }

                if (fileUploads.size() < attachments.size()) {
                    fileTooBig.accept(null);
                }
            } finally {
                fileUploads.forEach(fileUpload -> {
                    try {
                        fileUpload.close();
                    } catch (IOException exception) {
                        logger.error(String.valueOf(exception));
                        success.accept(false);
                    }
                });
            }
        });
    }
}
