//
// ------
//
// SAVOT Data Model
//
// Author:  Andr� Schaaff
// Address: Centre de Donnees astronomiques de Strasbourg
//          11 rue de l'Universite
//          67000 STRASBOURG
//          FRANCE
// Email:   schaaff@astro.u-strasbg.fr, question@simbad.u-strasbg.fr
//
// -------
//
// In accordance with the international conventions about intellectual
// property rights this software and associated documentation files
// (the "Software") is protected. The rightholder authorizes :
// the reproduction and representation as a private copy or for educational
// and research purposes outside any lucrative use,
// subject to the following conditions:
//
// The above copyright notice shall be included.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
// OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON INFRINGEMENT,
// LOSS OF DATA, LOSS OF PROFIT, LOSS OF BARGAIN OR IMPOSSIBILITY
// TO USE SUCH SOFWARE. IN NO EVENT SHALL THE RIGHTHOLDER BE LIABLE
// FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
// TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
// THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// For any other exploitation contact the rightholder.
//
//                        -----------
//
// Conformement aux conventions internationales relatives aux droits de
// propriete intellectuelle ce logiciel et sa documentation sont proteges.
// Le titulaire des droits autorise :
// la reproduction et la representation a titre de copie privee ou des fins
// d'enseignement et de recherche et en dehors de toute utilisation lucrative.
// Cette autorisation est faite sous les conditions suivantes :
//
// La mention du copyright portee ci-dessus devra etre clairement indiquee.
//
// LE LOGICIEL EST LIVRE "EN L'ETAT", SANS GARANTIE D'AUCUNE SORTE.
// LE TITULAIRE DES DROITS NE SAURAIT, EN AUCUN CAS ETRE TENU CONTRACTUELLEMENT
// OU DELICTUELLEMENT POUR RESPONSABLE DES DOMMAGES DIRECTS OU INDIRECTS
// (Y COMPRIS ET A TITRE PUREMENT ILLUSTRATIF ET NON LIMITATIF,
// LA PRIVATION DE JOUISSANCE DU LOGICIEL, LA PERTE DE DONNEES,
// LE MANQUE A GAGNER OU AUGMENTATION DE COUTS ET DEPENSES, LES PERTES
// D'EXPLOITATION,LES PERTES DE MARCHES OU TOUTES ACTIONS EN CONTREFACON)
// POUVANT RESULTER DE L'UTILISATION, DE LA MAUVAISE UTILISATION
// OU DE L'IMPOSSIBILITE D'UTILISER LE LOGICIEL, ALORS MEME
// QU'IL AURAIT ETE AVISE DE LA POSSIBILITE DE SURVENANCE DE TELS DOMMAGES.
//
// Pour toute autre utilisation contactez le titulaire des droits.
//

package cds.savot.model;

/**
* <p>Coosys element </p>
* @author Andre Schaaff
* @version 2.6 Copyright CDS 2002-2005
*  (kickoff 31 May 02)
*/
public class SavotCoosys extends MarkupComment implements SimpleTypes {

  // ID attribute
  protected char[] id = null;

  // equinox attribute
  protected char[] equinox = null;

  // epoch attribute
  protected char[] epoch = null;

  // system attribute eq_FK4, eq_FK5, ICRS, ecl_FK4, ecl_FK5, galactic, supergalactic, xy, barycentric, geo_app
  protected char[] system = "eq_FK5".toCharArray(); // default

  // element content
  char[] content = null;

  /**
   * Constructor
  */
  public SavotCoosys() {
  }

  /**
   * Set the id attribute
   * @param id
   */
  public void setId(String id) {
    if (id != null)
      this.id = id.toCharArray();
  }

  /**
   * Get the id attribute value
   * @return String
   */
  public String getId() {
    if (id != null)
      return String.valueOf(id);
    else return "";
  }

  /**
   * Set the equinox attribute
   * @param equinox ([JB]?[0-9]+([.][0-9]*)?)
   */
  public void setEquinox(String equinox) {
    if (equinox != null)
      this.equinox = equinox.toCharArray();
  }

  /**
   * Get the equinox attribute value
   * @return String
   */
  public String getEquinox() {
    if (equinox != null)
      return String.valueOf(equinox);
    else return "";
  }

  /**
   * Set the epoch attribute
   * @param epoch ([JB]?[0-9]+([.][0-9]*)?)
   */
  public void setEpoch(String epoch) {
    if (epoch != null)
      this.epoch = epoch.toCharArray();
  }

  /**
   * Get the epoch attribute value
   * @return String
   */
  public String getEpoch() {
    if (epoch != null)
      return String.valueOf(epoch);
    else return "";
  }

  /**
   * Set the system attribute
   * @param system (eq_FK4, eq_FK5, ICRS, ecl_FK4, ecl_FK5, galactic, supergalactic, xy, barycentric, geo_app)
   */
  public void setSystem(String system) {
    if (system != null)
      this.system = system.toCharArray();
  }

  /**
   * Get the system attribute value
   * @return String
   */
  public String getSystem() {
    if (system != null)
      return String.valueOf(system);
    else return "";
  }

/**
 * Set element content
 * @param content
*/
public void setContent(String content) {
  this.content = content.toCharArray();
}

/**
 * Get element content
 * @return a String
*/
public String getContent() {
  if (content != null)
    return String.valueOf(content);
  else return "";
}
}
