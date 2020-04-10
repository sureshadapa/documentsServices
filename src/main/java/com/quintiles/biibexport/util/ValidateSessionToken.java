package com.quintiles.biibexport.util;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ValidateSessionToken Utility class to check token in HSQLdb Sessions table. 
 */
public class ValidateSessionToken {
	
	private static final String SELECT_TOKEN = "select count(*) from Sessions where TokenValue =?";

    /**
     * Check if the passed in token is valid. This token wont be valid if its not in the database.
     *
     * @param token Token to be authenticated.
     * @return whether its a valid token
     * @throws SQLException Any Errors when checking the database.
     */
	public static boolean validateToken(String token) throws SQLException {
		boolean tokenflag = false;
		DatabaseUtil dbutil = DatabaseUtil.getInstance();
		Connection connection = dbutil.getConnection();
		PreparedStatement selectTokenStatement = connection.prepareStatement(SELECT_TOKEN);
		selectTokenStatement.setString(1,token);

		ResultSet rs = selectTokenStatement.executeQuery();
		rs.next();
		if(rs.getInt(1) > 0){
			tokenflag = true;
		}
		dbutil.close(connection);
		return tokenflag;
	}
}