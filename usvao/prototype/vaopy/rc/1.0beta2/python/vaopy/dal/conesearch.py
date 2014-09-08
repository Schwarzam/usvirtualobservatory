"""
The DAL Query interface specialized for Simple Cone Search (SSCS) services.
"""

import numbers
from . import query
from .query import DalQueryError

__all__ = [ "conesearch", "SSCSService", "SSCSQuery" ]

def conesearch(url, ra, dec, sr=1.0, verbosity=2):
    """
    submit a simple Cone Search query that requests objects or observations
    whose positions fall within some distance from a search position.  

    :Args:
       *url*        the base URL of the query service.
       *ra*:        the ICRS right ascension position of the center of the 
                      circular search region, in decimal degrees
       *dec*:       the ICRS declination position of the center of the 
                      circular search region, in decimal degrees
       *sr*:        the radius of the circular search region, in decimal degrees
       *verbosity*  an integer value that indicates the volume of columns
                       to return in the result table.  0 means the minimum
                       set of columsn, 3 means as many columns as are 
                       available. 
    """
    service = SCSService(url)
    return service.search(ra, dec, sr, verbosity)

class SCSService(query.DalService):
    """
    a representation of a Cone Search service
    """

    def __init__(self, baseurl, resmeta=None, version="1.0"):
        """
        instantiate a Cone Search service

        :Args:
           *baseurl*:  the base URL for submitting search queries to the 
                         service.
           *resmeta*:  an optional dictionary of properties about the 
                         service
        """
        query.DalService.__init__(self, baseurl, "scs", version, resmeta)

    def search(self, ra, dec, sr=1.0, verbosity=2):
        """
        submit a simple Cone Search query that requests objects or observations
        whose positions fall within some distance from a search position.  

        :Args:
           *ra*:        the ICRS right ascension position of the center of the 
                           circular search region, in decimal degrees
           *dec*:       the ICRS declination position of the center of the 
                           circular search region, in decimal degrees
           *sr*:        the radius of the circular search region, in decimal 
                           degrees
           *verbosity*  an integer value that indicates the volume of columns
                           to return in the result table.  0 means the minimum
                           set of columsn, 3 means as many columns as are 
                           available. 
        """
        q = self.create_query(ra, dec, sr, verbosity)
        return q.execute()

    def create_query(self, ra=None, dec=None, sr=None, verbosity=None):
        """
        create a query object that constraints can be added to and then 
        executed.  The input arguments will initialize the query with the 
        given values.

        :Args:
           *ra*:        the ICRS right ascension position of the center of the 
                           circular search region, in decimal degrees
           *dec*:       the ICRS declination position of the center of the 
                           circular search region, in decimal degrees
           *sr*:        the radius of the circular search region, in decimal 
                           degrees
           *verbosity*  an integer value that indicates the volume of columns
                           to return in the result table.  0 means the minimum
                           set of columsn, 3 means as many columns as are 
                           available. 
        """
        q = SCSQuery(self._baseurl)
        if ra  is not None:  q.ra  = ra
        if dec is not None:  q.dec = dec
        if sr  is not None:  q.sr  = sr
        if verbosity is not None: q.verbosity = verbosity
        return q

class SCSQuery(query.DalQuery):
    """
    a class for preparing an query to a Cone Search service.  Query constraints
    are added via its service type-specific methods.  The various execute()
    functions will submit the query and return the results.  

    The base URL for the query can be changed via the baseurl property.
    """

    def __init__(self, baseurl, version="1.0"):
        """
        initialize the query object with a baseurl
        """
        query.DalQuery.__init__(self, baseurl, "scs", version)
        

    @property
    def ra(self):
        """
        the right ascension part of the position constraint (default: None).
        """
        return self.getparam("RA")
    @ra.setter
    def ra(self, val):
        if val is not None:
            if not isinstance(val, numbers.Number):
                raise ValueError("ra constraint is not a number")
            while val < 0:
                val = val + 360.0
            while val >= 360.0:
                val = val - 360.0

        self.setparam("RA", val)
    @ra.deleter
    def ra(self):
        self.unsetparam("RA")

    @property
    def dec(self):
        """
        the declination part of the position constraint (default: None).
        """
        return self.getparam("DEC")
    @dec.setter
    def dec(self, val):
        if val is not None:
            if not isinstance(val, numbers.Number):
                raise ValueError("ra constraint is not a number")
            if val < -90.0 or val > 90.0:
                raise ValueError("dec constraint out-of-range: " + str(val))

        self.setparam("DEC", val)
    @dec.deleter
    def dec(self):
        self.unsetparam("DEC")

    @property
    def sr(self):
        """
        the radius of the circular (cone) search region.
        """
        return self.getparam("SR")
    @sr.setter
    def sr(self, val):
        if val is not None:
            if not isinstance(val, numbers.Number):
                raise ValueError("ra constraint is not a number")
            if val <= 0.0 or val > 180.0:
                raise ValueError("sr constraint out-of-range: " + val)

        self.setparam("SR", val)
    @sr.deleter
    def sr(self):
        self.unsetparam("SR")

    @property
    def verbosity(self):
        return self.getparam("VERB")
    @verbosity.setter
    def verbosity(self, val):
        # do a check on val
        if not isinstance(val, int):
            raise ValueError("verbosity value not an integer: " + val)
        self.setparam("VERB", val)
    @verbosity.deleter
    def verbosity(self):
        self.unsetparam("VERB")

    def execute(self):
        """
        submit the query and return the results as a Results subclass instance.
        This implimentation returns an SCSResults instance

        :Raises:
           *DalServiceError*: for errors connecting to or 
                              communicating with the service
           *DalQueryError*:   if the service responds with 
                              an error, including a query syntax error.  
        """
        return SCSResults(self.execute_votable(), self.getqueryurl(True))

    def execute_votable(self):
        """
        submit the query and return the results as an AstroPy votable instance

        :Raises:
           *DalServiceError*: for errors connecting to or 
                              communicating with the service
           *DalFormatError*:  for errors parsing the VOTable response
           *DalQueryError*:   for errors in the input query syntax
        """
        try: 
            from astropy.io.votable.exceptions import W22
        except ImportError, ex:
            raise RuntimeError("astropy votable not available")

        try:
            return query._votableparse(self.execute_stream().read)
        except query.DalAccessError:
            raise
        except W22, e:
            raise query.DalFormatError("Unextractable Error encoded in " +
                                       "deprecated DEFINITIONS element")
        except Exception, e:
            raise query.DalFormatError(e, self.getqueryurl())

    def getqueryurl(self, lax=False):
        """
        return the GET URL that encodes the current query.  This is the 
        URL that the execute functions will use if called next.  

        :Args:
           *lax*:  if False (default), a DalQueryError exception will be 
                      raised if any required parameters (RA, DEC, or SR)
                      are missing.  If True, no syntax checking will be 
                      done.  
        """
        out = query.DalQuery.getqueryurl(self)
        if not lax:
            if self.ra is None:
                raise DalQueryError("Query is missing an RA parameter", url=out)
            if self.dec is None:
                raise DalQueryError("Query is missing a DEC parameter", url=out)
            if self.sr is None:
                raise DalQueryError("Query is missing an SR parameter", url=out)
        return out

class SCSResults(query.DalResults):
    """
    Results from a Cone Search query.  It provides random access to records in 
    the response.  Alternatively, it can provide results via a Cursor 
    (compliant with the Python Database API) or an iterable.
    """

    def __init__(self, votable, url=None, version="1.0"):
        """
        initialize the cursor.  This constructor is not typically called 
        by directly applications; rather an instance is obtained from calling 
        a SCSQuery's execute().
        """
        query.DalResults.__init__(self, votable, url, "scs", version)
        self._scscols = {
            "ID_MAIN":         self.fieldname_with_ucd("ID_MAIN"),
            "POS_EQ_RA_MAIN":  self.fieldname_with_ucd("POS_EQ_RA_MAIN"),
            "POS_EQ_DEC_MAIN": self.fieldname_with_ucd("POS_EQ_DEC_MAIN")
            }
        self._recnames = { "id":  self._scscols["ID_MAIN"],
                           "ra":  self._scscols["POS_EQ_RA_MAIN"],
                           "dec": self._scscols["POS_EQ_DEC_MAIN"]
                           }

    def _findresultsresource(self, votable):
        if len(votable.resources) < 1:
            return None
        return votable.resources[0]

    def _findstatus(self, votable):
        # this is specialized according to the Conesearch standard

        # look first in the preferred location: just below the root VOTABLE
        info = self._findstatusinfo(votable.infos)
        if info:
            return (info.name, info.value)

        
        # look next in the result resource
        res = self._findresultsresource(votable)
        if res:
            # look for RESOURCE/INFO
            info = self._findstatusinfo(res.infos)
            if info:
                return (info.name, info.value)

            # if not there, check for a PARAM
            info = self._findstatusinfo(res.params)
            if info:
                return (info.name, info.value)

        # last resort:  VOTABLE/DEFINITIONS/PARAM
        # NOT SUPPORTED BY astropy; parser has been configured to 
        # raise W22 as exception instead.

        # assume it's okay
        return ("OK", "Successful Response")

    def _findstatusinfo(self, infos):
        # this can be overridden to specialize for a particular DAL protocol
        for info in infos:
            if info.name == "Error":
                return info
                
        

    def getrecord(self, index):
        """
        return a Cone Search result record that follows dictionary
        semantics.  The keys of the dictionary are those returned by this
        instance's fieldNames() function: either the column IDs or name, if 
        the ID is not set.  The returned record has additional accessor 
        methods for getting at stardard Cone Search response metadata (e.g. 
        ra, dec).
        """
        return SCSRecord(self, index)

class SCSRecord(query.Record):
    """
    a dictionary-like container for data in a record from the results of an
    Cone Search query, describing an available image.
    """

    def __init__(self, results, index):
        query.Record.__init__(self, results, index)
        self._ucdcols = results._scscols
        self._names = results._recnames

    @property
    def ra(self):
        """
        return the right ascension of the object or observation described by
        this record.
        """
        return self.get(self._names["ra"])

    @property
    def dec(self):
        """
        return the declination of the object or observation described by
        this record.
        """
        return self.get(self._names["dec"])

    @property
    def id(self):
        """
        return the identifying name of the object or observation described by
        this record.
        """
        return self.get(self._names["id"])



