///////////////////////////////////////////////////////////////////////////////
//
// JTOpen (IBM Toolbox for Java - OSS version)
//
// Filename: SQLVargraphic.java
//
// The source code contained herein is licensed under the IBM Public License
// Version 1.0, which has been approved by the Open Source Initiative.
// Copyright (C) 1997-2003 International Business Machines Corporation and
// others. All rights reserved.
//
///////////////////////////////////////////////////////////////////////////////

package com.ibm.as400.access;

import java.io.InputStream;
import java.io.StringReader;
import java.sql.Blob;
import java.sql.Clob;
/* ifdef JDBC40 */
import java.sql.NClob;
import java.sql.RowId;
/* endif */ 
import java.sql.SQLException;
/* ifdef JDBC40 */
import java.sql.SQLXML;
/* endif */ 
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.net.URL;

final class SQLVargraphic
extends SQLDataBase
{
    static final String copyright = "Copyright (C) 1997-2003 International Business Machines Corporation and others.";

    // Private data.
     private int                     length_;
    private int                     maxLength_;
    private String                  value_;
    private int                     ccsid_; //@cca1

    // Note: maxLength is in bytes not counting 2 for LL.
    //
    SQLVargraphic(int maxLength, SQLConversionSettings settings, int ccsid)  //@cca1
    {
        super(settings);
        length_         = 0;
        maxLength_      = maxLength;
        truncated_ = 0; outOfBounds_ = false;
        value_          = "";
        ccsid_          = ccsid;  //@cca1
    }

    public Object clone()
    {
        return new SQLVargraphic(maxLength_, settings_, ccsid_); //@cca1
    }

    // Added method trim() to trim the string.
    public void trim()
    {
        value_ = value_.trim();
    }

    //---------------------------------------------------------//
    //                                                         //
    // CONVERSION TO AND FROM RAW BYTES                        //
    //                                                         //
    //---------------------------------------------------------//

    public void convertFromRawBytes(byte[] rawBytes, int offset, ConvTable ccsidConverter)
    throws SQLException
    {
        length_ = BinaryConverter.byteArrayToUnsignedShort(rawBytes, offset);

        int bidiStringType = settings_.getBidiStringType();
        // if bidiStringType is not set by user, use ccsid to get value
        if(bidiStringType == -1)
            bidiStringType = ccsidConverter.bidiStringType_;

        BidiConversionProperties bidiConversionProperties = new BidiConversionProperties(bidiStringType);  //@KBA
        bidiConversionProperties.setBidiImplicitReordering(settings_.getBidiImplicitReordering());         //@KBA
        bidiConversionProperties.setBidiNumericOrderingRoundTrip(settings_.getBidiNumericOrdering());      //@KBA

        // If the field is VARGRAPHIC, length_ contains the number
        // of characters in the string, while the converter is expecting
        // the number of bytes. Thus, we need to multiply length_ by 2.
        value_ = ccsidConverter.byteArrayToString(rawBytes, offset+2, length_*2, bidiConversionProperties); //@KBC changed to use bidiConversionProperties instead of bidiStringType
    }

    public void convertToRawBytes(byte[] rawBytes, int offset, ConvTable ccsidConverter)
    throws SQLException
    {
        try
        {
            int bidiStringType = settings_.getBidiStringType();
            // if bidiStringType is not set by user, use ccsid to get value
            if(bidiStringType == -1)
                bidiStringType = ccsidConverter.bidiStringType_;

            BidiConversionProperties bidiConversionProperties = new BidiConversionProperties(bidiStringType);  //@KBA
            bidiConversionProperties.setBidiImplicitReordering(settings_.getBidiImplicitReordering());         //@KBA
            bidiConversionProperties.setBidiNumericOrderingRoundTrip(settings_.getBidiNumericOrdering());      //@KBA

            // The length in the first 2 bytes is actually the length in characters.
            byte[] temp = ccsidConverter.stringToByteArray(value_, bidiConversionProperties);   //@KBC changed to use bidiConversionProperties instead of bidiStringType
            BinaryConverter.unsignedShortToByteArray(temp.length/2, rawBytes, offset);

            if(temp.length > maxLength_)
            {
                maxLength_ = temp.length;
                JDError.throwSQLException(this, JDError.EXC_INTERNAL);
            }
            System.arraycopy(temp, 0, rawBytes, offset+2, temp.length);

            // The buffer we are filling with data is big enough to hold the entire field.
            // For varchar fields the actual data is often smaller than the field width.
            // That means whatever is in the buffer from the previous send is sent to the
            // system.  The data stream includes actual data length so the old bytes are not
            // written to the database, but the junk left over may decrease the effectiveness
            // of compression.  The following code will write hex 0s to the buffer when
            // actual length is less that field length.  Note the 0s are written only if
            // the field length is pretty big.  The data stream code (DBBaseRequestDS)
            // does not compress anything smaller than 1K.
            if((maxLength_ > 256) && (maxLength_ - temp.length > 16))
            {
                int stopHere = offset + 2 + maxLength_;
                for(int i=offset + 2 + temp.length; i<stopHere; i++)
                    rawBytes[i] = 0x00;
            }
        }
        catch(Exception e)
        {
            JDError.throwSQLException(this, JDError.EXC_INTERNAL, e);
        }
    }

    //---------------------------------------------------------//
    //                                                         //
    // SET METHODS                                             //
    //                                                         //
    //---------------------------------------------------------//

    public void set(Object object, Calendar calendar, int scale)
    throws SQLException
    {
        String value = null;

        if(object instanceof String)
            value = (String)object;

        else if(object instanceof Number)
            value = object.toString();

        else if(object instanceof Boolean)
        {
            // @PDC
            // if "translate boolean" == false, then use "0" and "1" values to match native driver
            if(settings_.getTranslateBoolean() == true)
                value = object.toString();  //"true" or "false"
            else
                value = ((Boolean)object).booleanValue() == true ? "1" : "0";
        }

        else if(object instanceof Time)
            value = SQLTime.timeToString((Time)object, settings_, calendar);

        else if(object instanceof Timestamp)
            value = SQLTimestamp.timestampToString((Timestamp)object, calendar);

        else if(object instanceof java.util.Date)
            value = SQLDate.dateToString((java.util.Date)object, settings_, calendar);

        else if(object instanceof URL)
            value = object.toString();

        else if(JDUtilities.JDBCLevel_ >= 20 && object instanceof Clob)
        {
            Clob clob = (Clob)object;
            value = clob.getSubString(1, (int)clob.length());
        }
/* ifdef JDBC40 */
        else if(object instanceof SQLXML) //@PDA jdbc40
        {
            SQLXML xml = (SQLXML)object;
            value = xml.getString();
        }
/* endif */ 
        if(value == null)
            JDError.throwSQLException(this, JDError.EXC_DATA_TYPE_MISMATCH);

        value_ = value;

        // Truncate if necessary.
        int valueLength = value_.length();

        int truncLimit = maxLength_ / 2;

        if(valueLength > truncLimit)
        {
            value_ = value_.substring(0, truncLimit);
            truncated_ = valueLength - truncLimit;
            outOfBounds_ = false;
        }
        else
            truncated_ = 0; outOfBounds_ = false;

        length_ = value_.length();
    }

    //---------------------------------------------------------//
    //                                                         //
    // DESCRIPTION OF SQL TYPE                                 //
    //                                                         //
    //---------------------------------------------------------//

    public int getSQLType()
    {
        return SQLData.VARGRAPHIC;
    }

    public String getCreateParameters()
    {
        return AS400JDBCDriver.getResource("MAXLENGTH");
    }

    public int getDisplaySize()
    {
        if(ccsid_ == 65535)    //@bingra
            return maxLength_; //@bingra
        else
            return maxLength_ / 2;
    }

    // JDBC 3.0
    public String getJavaClassName()
    {
        return "java.lang.String";
    }

    public String getLiteralPrefix()
    {
        return "\'";
    }

    public String getLiteralSuffix()
    {
        return "\'";
    }

    public String getLocalName()
    {
        return "VARGRAPHIC";
    }

    public int getMaximumPrecision()
    {
        return 16369;
    }

    public int getMaximumScale()
    {
        return 0;
    }

    public int getMinimumScale()
    {
        return 0;
    }

    public int getNativeType()
    {
        return 464;
    }

    public int getPrecision()
    {
        /* maxLength_ is in terms of bytes */
        return maxLength_ / 2 ;
    }

    public int getRadix()
    {
        return 0;
    }

    public int getScale()
    {
        return 0;
    }

    public int getType()
    {
        return java.sql.Types.VARCHAR;
    }

    public String getTypeName()
    {
        if( ccsid_ == 13488 || ccsid_ == 1200)  //@cca1
        	return "NVARCHAR";  //@cca1 same as native

    	return "VARGRAPHIC";
    }

    public boolean isSigned()
    {
        return false;
    }

    public boolean isText()
    {
        return true;
    }

    public int getActualSize()
    {
        return value_.length();
    }

    public int getTruncated()
    {
        return truncated_;
    }
    public boolean getOutOfBounds() {
      return outOfBounds_;
    }

    //---------------------------------------------------------//
    //                                                         //
    // CONVERSIONS TO JAVA TYPES                               //
    //                                                         //
    //---------------------------------------------------------//


    public InputStream getBinaryStream()
    throws SQLException
    {
        truncated_ = 0; outOfBounds_ = false;
        return new HexReaderInputStream(new StringReader(getString()));
    }

    public Blob getBlob()
    throws SQLException
    {
        truncated_ = 0; outOfBounds_ = false;
        try
        {
            byte[] bytes = BinaryConverter.stringToBytes(getString());
            return new AS400JDBCBlob(bytes, bytes.length);
        }
        catch(NumberFormatException nfe)
        {
            // this field contains non-hex characters
            JDError.throwSQLException(this, JDError.EXC_DATA_TYPE_MISMATCH, nfe);
            return null;
        }
    }

    public byte[] getBytes()
    throws SQLException
    {
        truncated_ = 0; outOfBounds_ = false;
        try
        {
            return BinaryConverter.stringToBytes(getString());
        }
        catch(NumberFormatException nfe)
        {
            // this field contains non-hex characters
            JDError.throwSQLException(this, JDError.EXC_DATA_TYPE_MISMATCH, nfe);
            return null;
        }
    }

    public Object getObject()
    throws SQLException
    {
        truncated_ = 0; outOfBounds_ = false;
        // This is written in terms of getString(), since it will
        // handle truncating to the max field size if needed.
        return getString();
    }


    public String getString()
    throws SQLException
    {
        truncated_ = 0; outOfBounds_ = false;
        // Truncate to the max field size if needed.
        // Do not signal a DataTruncation per the spec. @B1A
        int maxFieldSize = settings_.getMaxFieldSize();
        if((value_.length() > maxFieldSize) && (maxFieldSize > 0))
        {
            // @B1D truncated_ = value_.length() - maxFieldSize;
            return value_.substring(0, maxFieldSize);
        }
        else
        {
            // @B1D truncated_ = 0; outOfBounds_ = false;
            return value_;
        }
    }


    //@pda jdbc40
    public String getNString() throws SQLException
    {
        // Truncate to the max field size if needed.
        // Do not signal a DataTruncation per the spec.
        int maxFieldSize = settings_.getMaxFieldSize();
        if((value_.length() > maxFieldSize) && (maxFieldSize > 0))
        {
            return value_.substring(0, maxFieldSize);
        }
        else
        {
            return value_;
        }
    }

/* ifdef JDBC40 */
    //@pda jdbc40
    public RowId getRowId() throws SQLException
    {
        //
        //truncated_ = 0; outOfBounds_ = false;
        //try
        //{
        //    return new AS400JDBCRowId(BinaryConverter.stringToBytes(value_));
        //}
        //catch(NumberFormatException nfe)
        //{
        //    // this string contains non-hex characters
        //    JDError.throwSQLException(this, JDError.EXC_DATA_TYPE_MISMATCH, nfe);
        //    return null;
        //}
                //decided this is of no use
        JDError.throwSQLException(this, JDError.EXC_DATA_TYPE_MISMATCH);
        return null;
    }

    //@pda jdbc40
    public SQLXML getSQLXML() throws SQLException
    {
        //This is written in terms of getString(), since it will
        // handle truncating to the max field size if needed.
        truncated_ = 0; outOfBounds_ = false;
        return new AS400JDBCSQLXML(getString());
    }
/* endif */ 


}

