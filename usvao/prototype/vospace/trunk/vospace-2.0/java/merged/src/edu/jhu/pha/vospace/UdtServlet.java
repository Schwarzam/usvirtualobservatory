/*******************************************************************************
 * Copyright (c) 2012, Johns Hopkins University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Johns Hopkins University nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL Johns Hopkins University BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package edu.jhu.pha.vospace;

import java.io.InputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.http.HttpServlet;

import org.apache.log4j.Logger;

import edu.jhu.pha.vospace.api.SizeLimitInputStream;
import edu.jhu.pha.vospace.api.exceptions.InternalServerErrorException;
import edu.jhu.pha.vospace.api.exceptions.NotFoundException;
import edu.jhu.pha.vospace.api.exceptions.PermissionDeniedException;
import edu.jhu.pha.vospace.jobs.JobsProcessor;
import edu.jhu.pha.vospace.node.Node;
import edu.jhu.pha.vospace.node.NodeFactory;
import edu.jhu.pha.vospace.rest.JobDescription;
import edu.jhu.pha.vospace.rest.JobDescription.STATE;
import edu.jhu.pha.vospace.storage.StorageManager;
import edu.jhu.pha.vospace.storage.StorageManagerFactory;

import udt.UDTInputStream;
import udt.UDTOutputStream;
import udt.UDTReceiver;
import udt.UDTServerSocket;
import udt.UDTSocket;
import udt.util.Util;

public class UdtServlet extends HttpServlet implements Runnable {
	
	private static final long serialVersionUID = 8871136211551367030L;

	private static final Logger logger = Logger.getLogger(UdtServlet.class);
	
	private final int serverPort = 9000;

	private final ExecutorService threadPool=Executors.newFixedThreadPool(3);
	private Thread udtThread;
	
	@Override
	public void init() {
		udtThread = new Thread(this, "UDT Thread");
		udtThread.setDaemon(true);
		udtThread.start();
	}

	@Override
	public void destroy() {
		if(null != udtThread)
			udtThread.interrupt();
	}
	
	public void run(){
		UDTServerSocket server = null;
		try{
			UDTReceiver.connectionExpiryDisabled=true;
			InetAddress myHost=InetAddress.getLocalHost();
			logger.debug("Running the UDT server");
			server = new UDTServerSocket(myHost,serverPort);
			while(!udtThread.isInterrupted()){
				UDTSocket socket=server.accept();
				Thread.sleep(1000);
				threadPool.execute(new RequestRunner(socket));
			}
			logger.debug("Shutting down the UDT server");
			server.shutDown();
		}catch(Exception ex){
			server.shutDown();
			throw new RuntimeException(ex);
		}
	}
	
	public static class RequestRunner implements Runnable{

		private final static Logger logger=Logger.getLogger(RequestRunner.class.getName());

		private final UDTSocket socket;

		private final NumberFormat format=NumberFormat.getNumberInstance();

		private boolean verbose = false;

		private final boolean memMapped;
		public RequestRunner(UDTSocket socket){
			this.socket=socket;
			format.setMaximumFractionDigits(3);
			memMapped=false;//true;
		}

		public void run(){
			try{
				logger.debug("Handling request from "+socket.getSession().getDestination());
				UDTInputStream in=socket.getInputStream();
				UDTOutputStream out=socket.getOutputStream();
				byte[]readBuf=new byte[32768];
				ByteBuffer bb=ByteBuffer.wrap(readBuf);

				//read file name info 
				while(in.read(readBuf)==0)Thread.sleep(100);

				//how many bytes to read for the file name
				byte[] jobId = new byte[36];
				bb.get(jobId);

				JobDescription job = JobsProcessor.getJob(UUID.fromString(new String(jobId)));
				if(null == job)
					throw new NotFoundException("The job "+jobId+" is not found.");

				switch(job.getDirection()) {
				case PULLFROMVOSPACE: {
					
					JobsProcessor.modifyJobState(job, STATE.RUN);
					
					logger.debug("Sending node through UDT: "+job.getTarget());
					
					StorageManager backend = StorageManagerFactory.getInstance().getStorageManager(job.getUsername());

					InputStream dataInp = backend.getBytes(job.getTargetId().getNodePath());
					
					try {
						Node node = NodeFactory.getInstance().getNode(job.getTargetId(), job.getUsername());
						//long size = Long.parseLong(backend.getNodeSize(job.getTargetId().getNodePath()));
						long size = node.getNodeInfo().getSize();
						
						out.write(encode64(size));
						out.flush();
						
						long start=System.currentTimeMillis();
						
						Util.copy(dataInp, out, size, false);

						JobsProcessor.modifyJobState(job, STATE.COMPLETED);

						logger.debug("[SendFile] Finished sending data.");
						long end=System.currentTimeMillis();
						logger.debug(socket.getSession().getStatistics().toString());
						double rate=1000.0*size/1024/1024/(end-start);
						logger.debug("[SendFile] Rate: "+format.format(rate)+" MBytes/sec. "+format.format(8*rate)+" MBit/sec.");
						logger.debug("Finished request from "+socket.getSession().getDestination());
					} catch(InternalServerErrorException ex) {
						JobsProcessor.modifyJobState(job, STATE.ERROR);
						throw ex;
					} catch(NotFoundException ex) {
						JobsProcessor.modifyJobState(job, STATE.ERROR);
						throw ex;
					} catch(PermissionDeniedException ex) {
						JobsProcessor.modifyJobState(job, STATE.ERROR);
						throw ex;
					}finally{
						logger.debug("Closing all the stuff");
						try {socket.getSender().stop();} catch(Exception ex) {}
						try {socket.close();} catch(Exception ex) {}
						logger.debug("Closed all the stuff");
					}
					break;
				}
				
				case PUSHTOVOSPACE: {
					JobsProcessor.modifyJobState(job, STATE.RUN);
					
					logger.debug("Receiving node through UDT: "+job.getTarget());
					
					StorageManager backend = StorageManagerFactory.getInstance().getStorageManager(job.getUsername());

					try {

						byte[]sizeInfo=new byte[8];
						int total=0;
						while(total<sizeInfo.length){
							int r=in.read(sizeInfo);
							if(r<0)break;
							total+=r;
						}
						long size=decode(sizeInfo, 0);
						
						long start=System.currentTimeMillis();
						
						backend.putBytes(job.getTargetId().getNodePath(), new SizeLimitInputStream(in, size));
						logger.debug("Got the file");
						long end=System.currentTimeMillis();

						JobsProcessor.modifyJobState(job, STATE.COMPLETED);

						logger.debug("[ReceiveFile] Finished receiving data.");
						logger.debug(socket.getSession().getStatistics().toString());
						double rate=1000.0*size/1024/1024/(end-start);
						logger.debug("[ReceiveFile] Rate: "+format.format(rate)+" MBytes/sec. "+format.format(8*rate)+" MBit/sec.");
						logger.debug("Finished request from "+socket.getSession().getDestination());
					} catch(InternalServerErrorException ex) {
						JobsProcessor.modifyJobState(job, STATE.ERROR);
						throw ex;
					} catch(NotFoundException ex) {
						JobsProcessor.modifyJobState(job, STATE.ERROR);
						throw ex;
					} catch(PermissionDeniedException ex) {
						JobsProcessor.modifyJobState(job, STATE.ERROR);
						throw ex;
					}finally{
						logger.debug("Closing all the stuff");
						try {socket.getSender().stop();} catch(Exception ex) {}
						try {socket.close();} catch(Exception ex) {}
						logger.debug("Closed all the stuff");
					}
					break;
				}
				
				default:
					throw new InternalServerErrorException("The job "+job.getDirection()+" is unsupported in this path.");
				}
			}catch(Exception ex){
				ex.printStackTrace();
				throw new RuntimeException(ex);
			}
		}
	}
	
    static byte[]encode64(long value){
        byte m4= (byte) (value>>24 );
        byte m3=(byte)(value>>16);
        byte m2=(byte)(value>>8);
        byte m1=(byte)(value);
        return new byte[]{m1,m2,m3,m4,0,0,0,0};
    }
    
	static long decode(byte[]data, int start){
		long result = (data[start+3] & 0xFF)<<24
		             |(data[start+2] & 0xFF)<<16
					 |(data[start+1] & 0xFF)<<8
					 |(data[start] & 0xFF);
		return result;
	}
}


