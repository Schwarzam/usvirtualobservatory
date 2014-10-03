/*******************************************************************************
 * Copyright (c) 2011, Johns Hopkins University
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
package org.usvao.descriptors.uwsdesc;

/**
 * UWS resources associated with each job. 
 * @author deoyani nandrekar-heinis
 */

public class UWSJobDescription {

    private String jobId;
    private String phase;
    private long starttime;
    private long endtime;
    private long destruction;
    private long duration;
    private String[] parameters = new String[4];
    private String query;
    private String owner;
    private String error;
    private Boolean results = false;
    private Boolean accessError = false;    
    private String adql;
    private String resultLink;
    private String runId;
    private int maxrec;
   
    private String uploadparams;

    public UWSJobDescription(){
    }
    
    public UWSJobDescription(String jobId, String phase, long starttime, long endtime,
                             long destruction, long duration, String[] parameters, 
                             String query, String owner,Boolean results, String error, String adql)
    {
        this.jobId      = jobId;
        this.phase      = phase;
        this.starttime  = starttime;
        this.endtime    = endtime;
        this.destruction= destruction;
        this.duration   = duration;
        this.parameters = parameters;
        this.query      = query;
        this.owner      = owner;
        this.results    = results;
        this.error      = error;
        this.adql       = adql;
    }

    public void setRunId(String runid){
        this.runId = runid;
    }
    public void setJobId(String jobId){
        this.jobId = jobId;
    }
    public void setPhase(String phase){
        this.phase = phase;
    }
    public void setStarttime(long starttime){
        this.starttime = starttime;
    }
    public void setEndtime(long endtime){
        this.endtime = endtime;
    }
    public void setDestruction(long destruction){
        this.destruction = destruction;
    }
    public void setDuration(long duration){
        this.duration = duration;
    }
    public void setParameters(String[] parameters){
        this.parameters = new String[parameters.length];        
        for(int i=0; i<parameters.length;i++)
        this.parameters[i] = parameters[i];
    }
    public void setQuery(String query){
        this.query = query;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }
    public void setError(String error){
        this.error = error;
    }
    public void setAccessError(boolean access){
        this.accessError = access;
    }    
//    public void setUploadedtables(String value) {
//       uploadedtables = value;
//    }
    public void setUploadParams(String value){
        this.uploadparams = value;
    }
    public void setResultLink(String value){
        this.resultLink = value;
    }
    public void setMaxrec(int value){
        this.maxrec = value;
    }
    
    
    
    public String getJobId(){
        return this.jobId ;
    }
    public String getPhase(){
        return this.phase;
    }
    public long getStarttime(){
        return this.starttime;
    }
    public long getEndtime(){
        return this.endtime;
    }
    public long getDestruction(){
        return this.destruction;
    }
    public long getDuration(){
        return this.duration;
    }
    public String[] getParameters(){
        return this.parameters;
    }
    public String getQuery(){
        return this.query;
    }   
    public String getOwner(){
        return this.owner;
    }
    public boolean getResults() {
       return this.results;
    }
    public String getError(){
        return this.error;
    }
    public boolean getaccessError(){
        return this.accessError;
    }
//    public String getUploadedtables(){
//        return uploadedtables;
//    }
    public String getUploaadParams(){
        return this.uploadparams;
    }
    public String getResultLink(){
        return this.resultLink;
    }
    public String getRunId(){
        return this.runId;
    }
    
    public int getMaxrec(){
        return this.maxrec;
    }
}