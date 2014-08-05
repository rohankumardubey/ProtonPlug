/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.proton.plug.test.minimalserver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.proton.plug.AMQPConnection;
import org.proton.plug.context.ProtonConnectionCallback;
import org.proton.plug.context.ProtonSessionCallback;
import org.proton.plug.util.ByteUtil;
import org.proton.plug.util.DebugInfo;

/**
 * @author Clebert Suconic
 */

public class MinimalConnectionSPI implements ProtonConnectionCallback
{
   Channel channel;

   private AMQPConnection connection;

   public MinimalConnectionSPI(Channel channel)
   {
      this.channel = channel;
   }

   ExecutorService executorService = Executors.newSingleThreadExecutor();

   @Override
   public void close()
   {
      executorService.shutdown();
   }

   public void setConnection(AMQPConnection connection)
   {
      this.connection = connection;
   }

   public AMQPConnection getConnection()
   {
      return connection;
   }



   @Override
   public void onTransport(final ByteBuf bytes, final AMQPConnection connection)
   {

      final int bufferSize = bytes.writerIndex();

      if (DebugInfo.debug)
      {
         // some debug
         byte[] frame = new byte[bytes.writerIndex()];
         int readerOriginalPos = bytes.readerIndex();

         bytes.getBytes(0, frame);

         try
         {
            System.err.println("Buffer Outgoing: " + "\n" + ByteUtil.formatGroup(ByteUtil.bytesToHex(frame), 4, 16));
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }

         bytes.readerIndex(readerOriginalPos);
      }


      // ^^ debug

      channel.writeAndFlush(bytes).addListener(new ChannelFutureListener()
      {
         @Override
         public void operationComplete(ChannelFuture future) throws Exception
         {
//            connection.outputDone(bufferSize);
         }
      });
      connection.outputDone(bufferSize);
   }

   @Override
   public ProtonSessionCallback createSessionSPI(AMQPConnection connection)
   {
      return new MinimalSessionSPI();
   }
}