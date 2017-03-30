/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/logger-java-log4j
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.ta.reportportal.log4j.appender;

import com.epam.reportportal.message.HashMarkSeparatedMessageParser;
import com.epam.reportportal.message.MessageParser;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import rp.com.google.common.base.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

/**
 * Log4j appender for report portal
 *
 * @author Andrei Varabyeu
 */
public class ReportPortalAppender extends AppenderSkeleton {

    private static final MessageParser MESSAGE_PARSER = new HashMarkSeparatedMessageParser();

    @Override
    protected void append(final LoggingEvent event) {

        if (null == event.getMessage()) {
            return;
        }

        ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
            @Nonnull
            @Override
            public SaveLogRQ apply(@Nullable String itemId) {

                SaveLogRQ rq = new SaveLogRQ();
                rq.setLevel(event.getLevel().toString());
                rq.setLogTime(new Date(event.getTimeStamp()));
                rq.setTestItemId(itemId);

                String logMessage = null;
                try {
                    ReportPortalMessage message = null;

                    /*
                    * If additional parameter used in logger, for example:
		            * org.apache.log4j.Logger.debug("message", new Throwable()); Then add
		            * stack-trace to logged message string
		            */
                    StringBuilder throwable = new StringBuilder();
                    if (null != event.getThrowableInformation()) {
                        for (String oneLine : event.getThrowableStrRep())
                            throwable.append(oneLine);
                    }

                    // ReportPortalMessage is reported
                    if (ReportPortalMessage.class.equals(event.getMessage().getClass())) {
                        message = (ReportPortalMessage) event.getMessage();

                        // File is reported
                    } else if (File.class.equals(event.getMessage().getClass())) {
                        message = new ReportPortalMessage((File) event.getMessage(), "Binary data reported");

                        // Parsable String is reported
                    } else if (String.class.equals(event.getMessage().getClass()) && MESSAGE_PARSER
                            .supports((String) event.getMessage())) {
                        message = MESSAGE_PARSER.parse((String) event.getMessage());
                    }

                    // There is some binary data reported
                    if (null != message) {
                        logMessage = message.getMessage();

                        SaveLogRQ.File file = new SaveLogRQ.File();
                        file.setContentType(message.getData().getMediaType());
                        file.setContent(message.getData().read());
                        file.setName(UUID.randomUUID().toString());
                        rq.setFile(file);

                    } else {
                        // Plain string message is reported
                        if (ReportPortalAppender.this.layout == null) {
                            logMessage = event.getRenderedMessage();
                        } else {
                            logMessage = ReportPortalAppender.this.layout.format(event).concat(throwable.toString());
                        }
                    }

                } catch (IOException e) {
                    //do nothing
                }

                rq.setMessage(logMessage);
                return rq;

            }
        });

    }

    @Override
    public void close() {
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }

}
