#!/usr/bin/perl
#
# stupid little lynx clone that does not ignore 206's
#
# Hussein Suleman
# Virginia Tech Digital Library Research Laboratory
# August 2001
#
# expects parameters in the form:
#   lynx.pl -error_file=xyz -source "http://someurl"
#

use LWP;

# get arguments
my $error_file = (split ('=', $ARGV[0]))[1];
my $url = $ARGV[2];
my $from = $ENV{'REMOTE_ADDR'};

# create a user agent object
use LWP::UserAgent;
$ua = new LWP::UserAgent;
$ua->agent("RepositoryExplorer/2.0b2-1.44 " . $ua->agent);
# $ua->proxy(['http', 'ftp'], 'http://cache.uct.ac.za:8080/');
# $ua->no_proxy ('localhost', 'no', 'uct.ac.za', 'no');

# create a request
my $req = new HTTP::Request GET => $url;
if ((defined $from) && ($from ne ''))
{
   $req->header ('X-Forwarded-For' => $from);
}

my $state = 0;
my $res;
my $retries = 0;
while ($state == 0)
{
   # pass request to the user agent and get a response back
   $res = $ua->request($req);

   if ($res->code == 503)
   {
      my $sleep = $res->header ('Retry-After');
      if (not defined ($sleep) || ($sleep < 0) || ($sleep > 86400))
      { $state = 1; }
      else
      {
         $retries++;
         if ($retries == 5)
         { $state = 1; }
         else
         { sleep ($sleep); }
      }
   }
   else
   { $state = 1; }
}

# print the error file
open (ERROR, ">$error_file");
print ERROR "   URL=$url (GET)\n";
print ERROR "STATUS=".$res->protocol." ".$res->code." ".$res->message."\n";
close (ERROR);

# search/replace on schema locations
# $res->content =~ s/http:\/\/www.openarchives.org\/OAI\/(1.0\/)?((dc|rfc1807|oai_marc|oai-identifier|eprints|OAI_Identify|OAI_GetRecord|OAI_ListMetadataFormats|OAI_ListSets|OAI_ListIdentifiers|OAI_ListRecords).xsd)/$2/g;

# print output data
if (length ($res->content) < 1000000)
{
   print $res->content;
}
else
{
   print "content too large!\n";
}
