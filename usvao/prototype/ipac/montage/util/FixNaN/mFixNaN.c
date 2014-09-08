/*************************************************************************

   Copyright (c) 2014 California Institute of Technology, Pasadena,
   California.    Based on Cooperative Agreement Number NCC5-626 between
   NASA and the California Institute of Technology. All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   of this BSD 3-clause license are met:

   1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

   3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

   This software was developed at the Infrared Processing and Analysis
   Center (IPAC) and the Jet Propulsion Laboratory (JPL) by Bruce
   Berriman, John Good, Joseph Jacob, Daniel S. Katz, and Anastasia
   Laity.

*************************************************************************/



/* Module: mFixNaN.c

Version  Developer        Date     Change
-------  ---------------  -------  -----------------------
2.1      John Good        07Oct07  Bug fix: set fstatus value
2.0      John Good        11Aug05  Change the code to do a line at a time
                                   (to mimimize memory usage) and the allow 
                                   for no output image (just a count of pixel
                                   that would change) when the output file
                                   name is "-".
1.0      John Good        16Sep04  Baseline code

*/

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <time.h>
#include <math.h>

#include "montage.h"
#include "fitsio.h"
#include "wcs.h"
#include "mNaN.h"

#define MAXSTR  256
#define MAXFILE 256

char input_file  [MAXSTR];
char output_file [MAXSTR];

void printFitsError(int err);
void printError    (char *msg);
int  readFits      (char *fluxfile);
int  checkHdr      (char *infile, int hdrflag, int hdu);

int  debug;

struct
{
   fitsfile *fptr;
   long      naxes[2];
   double    crpix1, crpix2;
}
   input, output;

static time_t currtime, start;


/*************************************************************************/
/*                                                                       */
/*  mFixNaN                                                              */
/*                                                                       */
/*  Montage is a set of general reprojection / coordinate-transform /    */
/*  mosaicking programs.  Any number of input images can be merged into  */
/*  an output FITS file.  The attributes of the input are read from the  */
/*  input files; the attributes of the output are read a combination of  */
/*  the command line and a FITS header template file.                    */
/*                                                                       */
/*  This module, mFixNaN, converts NaNs found in the image to some       */
/*  other value (given by the user) or ranges of values to NaNs          */
/*                                                                       */
/*************************************************************************/

int main(int argc, char **argv)
{
   int       i, j, k, countRange, countNaN, status;
   int       nMinMax, inRange, haveVal, writeOutput;
   long      fpixel[4], nelements;
   double   *inbuffer;
   double    NaNvalue;

   double    minblank[256], maxblank[256];
   int       ismin[256], ismax[256];
   double   *outbuffer;

   char     *end;


   /************************************************/
   /* Make a NaN value to use setting blank pixels */
   /************************************************/

   union
   {
      double d;
      char   c[8];
   }
   value;

   double nan;

   for(i=0; i<8; ++i)
      value.c[i] = 255;

   nan = value.d;


   /***************************************/
   /* Process the command-line parameters */
   /***************************************/

   fstatus = stdout;

   debug   = 0;
   haveVal = 0;

   writeOutput = 1;

   for(i=0; i<argc; ++i)
   {
      if(strcmp(argv[i], "-d") == 0)
      {
         if(i+1 >= argc)
         {
            printf("[struct stat=\"ERROR\", msg=\"No debug level given\"]\n");
            exit(1);
         }

         debug = strtol(argv[i+1], &end, 0);

         if(end - argv[i+1] < strlen(argv[i+1]))
         {
            printf("[struct stat=\"ERROR\", msg=\"Debug level string is invalid: '%s'\"]\n", argv[i+1]);
            exit(1);
         }

         if(debug < 0)
         {
            printf("[struct stat=\"ERROR\", msg=\"Debug level value cannot be negative\"]\n");
            exit(1);
         }

         argv += 2;
         argc -= 2;
      } 

      if(strcmp(argv[i], "-v") == 0)
      {
         if(i+1 >= argc)
         {
            printf("[struct stat=\"ERROR\", msg=\"No value given for NaN conversion\"]\n");
            exit(1);
         }

         NaNvalue = strtod(argv[i+1], &end);

         if(end - argv[i+1] < strlen(argv[i+1]))
         {
            printf("[struct stat=\"ERROR\", msg=\"NaN conversion value string is invalid: '%s'\"]\n", argv[i+1]);
            exit(1);
         }

         haveVal = 1;

         argv += 2;
         argc -= 2;
      }
   }
   
   if (argc < 3) 
   {
      printf ("[struct stat=\"ERROR\", msg=\"Usage: %s [-d level][-v NaN-value] in.fits out.fits [minblank maxblank] (output file name '-' means no file; min/max ranges can be repeated and can be the words 'min' and 'max')\"]\n", argv[0]);
      exit(1);
   }

   strcpy(input_file,  argv[1]);

   if(input_file[0] == '-')
   {
      printf ("[struct stat=\"ERROR\", msg=\"Invalid input file '%s'\"]\n", input_file);
      exit(1);
   }

   strcpy(output_file, argv[2]);

   if(output_file[0] == '-')
      writeOutput = 0;


   nMinMax = 0;

   if(argc > 3)
   {
      while(1)
      {
         if(argc < 5+2*nMinMax)
            break;

         ismin[nMinMax] = 0;

         if(strcmp(argv[3+2*nMinMax], "min") == 0)
         {
            ismin[nMinMax] = 1;

            minblank[nMinMax]= 0.;
         }
         else
         {
            minblank[nMinMax] = strtod(argv[3+2*nMinMax], &end);

            if(end < argv[3] + strlen(argv[3+2*nMinMax]))
            {
               printf ("[struct stat=\"ERROR\", msg=\"min blank value string is not a number\"]\n");
               exit(1);
            }
         }


         ismax[nMinMax] = 0;

         if(strcmp(argv[4+2*nMinMax], "max") == 0)
         {
            ismax[nMinMax] = 1;

            maxblank[nMinMax]= 0.;
         }
         else
         {
            maxblank[nMinMax] = strtod(argv[4+2*nMinMax], &end);

            if(end < argv[4] + strlen(argv[4+2*nMinMax]))
            {
               printf ("[struct stat=\"ERROR\", msg=\"max blank value string is not a number\"]\n");
               exit(1);
            }
         }

         ++nMinMax;
      }
   }

   if(debug >= 1)
   {
      printf("input_file       = [%s]\n", input_file);
      printf("output_file      = [%s]\n", output_file);

      for(j=0; j<nMinMax; ++j)
      {
         printf("minblank[%d]    = %-g (%d)\n",  j, minblank[j], ismin[j]);
         printf("maxblank[%d]    = %-g (%d)\n",  j, maxblank[j], ismax[j]);
      }

      fflush(stdout);
   }


   /************************/
   /* Read the input image */
   /************************/

   time(&currtime);
   start = currtime;

   readFits(input_file);

   if(debug >= 1)
   {
      printf("input.naxes[0]       =  %ld\n",   input.naxes[0]);
      printf("input.naxes[1]       =  %ld\n",   input.naxes[1]);
      printf("input.crpix1         =  %-g\n",   input.crpix1);
      printf("input.crpix2         =  %-g\n",   input.crpix2);

      fflush(stdout);
   }

   output.naxes[0] = input.naxes[0];
   output.naxes[1] = input.naxes[1];
   output.crpix1   = input.crpix1;
   output.crpix2   = input.crpix2;


   if(writeOutput)
   {
      /********************************/
      /* Create the output FITS files */
      /********************************/

      remove(output_file);               

      status = 0;
      if(fits_create_file(&output.fptr, output_file, &status)) 
         printFitsError(status);           

      if(debug >= 1)
      {
         printf("\nFITS output file created (not yet populated)\n"); 
         fflush(stdout);
      }


      /********************************/
      /* Copy all the header keywords */
      /* from the input to the output */
      /********************************/

      status = 0;
      if(fits_copy_header(input.fptr, output.fptr, &status))
         printFitsError(status);           

      if(debug >= 1)
      {
         printf("Header keywords copied to FITS output file\n\n"); 
         fflush(stdout);
      }


      /***************************/
      /* Modify BITPIX to be -64 */
      /***************************/

      status = 0;
      if(fits_update_key_lng(output.fptr, "BITPIX", -64,
                             (char *)NULL, &status))
         printFitsError(status);
   }

   /*****************************************************/ 
   /* Allocate memory for the input/output image pixels */ 
   /* (same size as the input image)                    */ 
   /*****************************************************/ 

   outbuffer = (double *)malloc(output.naxes[0] * sizeof(double));

   if(debug >= 1)
   {
      printf("%ld bytes allocated for row of output image pixels\n", 
         output.naxes[0] * sizeof(double));
      fflush(stdout);
   }

   inbuffer = (double *)malloc(input.naxes[0] * sizeof(double));

   if(debug >= 1)
   {
      printf("%ld bytes allocated for row of input image pixels\n", 
         input.naxes[0] * sizeof(double));
      fflush(stdout);
   }


   /*****************************/
   /* Loop over the input lines */
   /*****************************/

   fpixel[0] = 1;
   fpixel[1] = 1;
   fpixel[2] = 1;
   fpixel[3] = 1;

   nelements = input.naxes[0];

   countRange = 0;
   countNaN   = 0;

   for (j=0; j<input.naxes[1]; ++j)
   {
      if(debug >= 2)
      {
         if(debug >= 3)
            printf("\n");

         printf("\rProcessing input row %5d [So far rangeCount=%d, nanCount=%d]",
            j, countRange, countNaN);

         if(debug >= 3)
            printf("\n");

         fflush(stdout);
      }

      for (i=0; i<output.naxes[0]; ++i)
         outbuffer[i] = 0.;


      /***********************************/
      /* Read a line from the input file */
      /***********************************/

      status = 0;
      if(fits_read_pix(input.fptr, TDOUBLE, fpixel, nelements, NULL,
                       inbuffer, NULL, &status))
         printFitsError(status);
      

      /************************/
      /* For each input pixel */
      /************************/

      // If not-a-number and we have a replacement value

      for (i=0; i<input.naxes[0]; ++i)
      {
         if(mNaN(inbuffer[i]) && haveVal) 
         {
            ++countNaN;

            outbuffer[i] = NaNvalue;

            if(debug >= 3)
            {
               printf("pixel[%d][%d] converted to %-g\n", j, i, NaNvalue);
               fflush(stdout);
            }
         }


         // If in one of the "fix" ranges

         else if(nMinMax)
         {
            inRange = 0;

            for(k=0; k<nMinMax; ++k)
            {
               if(ismin[k] && inbuffer[i] <= maxblank[k])
               {
                  inRange = 1;
                  break;
               }
               else if(ismax[k] && inbuffer[i] >= minblank[k])
               {
                  inRange = 1;
                  break;
               }
               else if(inbuffer[i] >= minblank[k] 
                    && inbuffer[i] <= maxblank[k])
               {
                  inRange = 1;
                  break;
               }
            }

            if(inRange)
            {
               ++countRange;

               if(haveVal) 
               {
                  ++countNaN;

                  outbuffer[i] = NaNvalue;

                  if(debug >= 3)
                  {
                     printf("pixel[%d][%d] converted to NaN -> %-g\n", j, i, NaNvalue);
                     fflush(stdout);
                  }
               }
               else
               {
                  ++countNaN;

                  outbuffer[i] = nan;

                  if(debug >= 3)
                  {
                     printf("pixel[%d][%d] converted to NaN\n", j, i);
                     fflush(stdout);
                  }
               }
            }

            else
               outbuffer[i] = inbuffer[i];
         }


         // Otherwise, good data

         else
            outbuffer[i] = inbuffer[i];
      }


      /***************************/
      /* Write the output buffer */
      /***************************/

      if(writeOutput)
      {
         status = 0;
         if (fits_write_pix(output.fptr, TDOUBLE, fpixel, nelements, 
                            outbuffer, &status))
            printFitsError(status);
      }

      ++fpixel[1];
   }


   if(debug >= 1)
   {
      time(&currtime);
      printf("\nDone copying data (%d seconds)\n", 
         (int)(currtime - start));
      fflush(stdout);
   }


   /************************/
   /* Close the FITS files */
   /************************/

   status = 0;
   if(fits_close_file(input.fptr, &status))
      printFitsError(status);

   if(writeOutput)
   {
      status = 0;
      if(fits_close_file(output.fptr, &status))
         printFitsError(status);           
   }

   if(debug >= 1)
   {
      time(&currtime);
      printf("Done (%d seconds total)\n", (int)(currtime - start));
      fflush(stdout);
   }

   free(inbuffer);
   free(outbuffer);

   printf("[struct stat=\"OK\", rangeCount=%d, nanCount=%d]\n", countRange, countNaN);
   fflush(stdout);

   exit(0);
}


/*******************************************/
/*                                         */
/*  Open a FITS file pair and extract the  */
/*  pertinent header information.          */
/*                                         */
/*******************************************/

int readFits(char *fluxfile)
{
   int    status, nfound;
   long   naxes[2];
   double crpix[2];
   char   errstr[MAXSTR];

   checkHdr(fluxfile, 0, 0);

   status = 0;
   if(fits_open_file(&input.fptr, fluxfile, READONLY, &status))
   {
      sprintf(errstr, "Image file %s missing or invalid FITS", fluxfile);
      printError(errstr);
   }

   status = 0;
   if(fits_read_keys_lng(input.fptr, "NAXIS", 1, 2, naxes, &nfound, &status))
      printFitsError(status);
   
   input.naxes[0] = naxes[0];
   input.naxes[1] = naxes[1];

   status = 0;
   if(fits_read_keys_dbl(input.fptr, "CRPIX", 1, 2, crpix, &nfound, &status))
      printFitsError(status);

   input.crpix1 = crpix[0];
   input.crpix2 = crpix[1];

   return 0;
}



/******************************/
/*                            */
/*  Print out general errors  */
/*                            */
/******************************/

void printError(char *msg)
{
   fprintf(stderr, "[struct stat=\"ERROR\", msg=\"%s\"]\n", msg);
   exit(1);
}




/***********************************/
/*                                 */
/*  Print out FITS library errors  */
/*                                 */
/***********************************/

void printFitsError(int status)
{
   char status_str[FLEN_STATUS];

   fits_get_errstatus(status, status_str);

   printf("[struct stat=\"ERROR\", status=%d, msg=\"%s\"]\n", status, status_str);

   exit(1);
}

