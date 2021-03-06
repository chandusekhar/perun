package cz.metacentrum.perun.core.impl.modules.pwdmgr;

import cz.metacentrum.perun.core.api.BeansUtils;
import cz.metacentrum.perun.core.api.CoreConfig;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.rt.EmptyPasswordRuntimeException;
import cz.metacentrum.perun.core.api.exceptions.rt.LoginNotExistsRuntimeException;
import cz.metacentrum.perun.core.api.exceptions.rt.PasswordChangeFailedRuntimeException;
import cz.metacentrum.perun.core.api.exceptions.rt.PasswordCreationFailedRuntimeException;
import cz.metacentrum.perun.core.api.exceptions.rt.PasswordDeletionFailedRuntimeException;
import cz.metacentrum.perun.core.api.exceptions.rt.PasswordDoesntMatchRuntimeException;
import cz.metacentrum.perun.core.api.exceptions.rt.PasswordOperationTimeoutRuntimeException;
import cz.metacentrum.perun.core.api.exceptions.rt.PasswordStrengthFailedRuntimeException;
import cz.metacentrum.perun.core.implApi.modules.pwdmgr.PasswordManagerModule;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Generic implementation of {@link PasswordManagerModule}. It runs generic password manger script
 * defined as perun config in {@link CoreConfig#getPasswordManagerProgram()} or
 * {@link CoreConfig#getAlternativePasswordManagerProgram()}.
 *
 * @author Pavel Zlamal <zlamal@cesnet.cz>
 */
public class GenericPasswordManagerModule implements PasswordManagerModule {

	protected static final String PASSWORD_VALIDATE = "validate";
	protected static final String PASSWORD_CREATE = "create";
	protected static final String PASSWORD_RESERVE = "reserve";
	protected static final String PASSWORD_RESERVE_RANDOM = "reserve_random";
	protected static final String PASSWORD_CHANGE = "change";
	protected static final String PASSWORD_CHECK = "check";
	protected static final String PASSWORD_DELETE = "delete";

	protected static final String binTrue = "/bin/true";
	protected String actualLoginNamespace = "generic";
	protected String passwordManagerProgram = BeansUtils.getCoreConfig().getPasswordManagerProgram();
	protected String altPasswordManagerProgram = BeansUtils.getCoreConfig().getAlternativePasswordManagerProgram();

	public String getActualLoginNamespace() {
		return actualLoginNamespace;
	}

	public void setActualLoginNamespace(String actualLoginNamespace) {
		this.actualLoginNamespace = actualLoginNamespace;
	}

	@Override
	public Map<String, String> generateAccount(PerunSession session, Map<String, String> parameters) throws InternalErrorException {
		// account generation is not supported
		return null;
	}

	@Override
	public void reservePassword(PerunSession session, String userLogin, String password) throws InternalErrorException {
		if (StringUtils.isBlank(password)) {
			throw new EmptyPasswordRuntimeException("Password for " + actualLoginNamespace + ":" + userLogin + " cannot be empty.");
		}
		Process process = createPwdManagerProcess(PASSWORD_RESERVE, actualLoginNamespace, userLogin);
		sendPassword(process, password);
		handleExit(process, actualLoginNamespace, userLogin);
	}

	@Override
	public void reserveRandomPassword(PerunSession session, String userLogin) throws InternalErrorException {
		Process process = createPwdManagerProcess(PASSWORD_RESERVE_RANDOM, actualLoginNamespace, userLogin);
		handleExit(process, actualLoginNamespace, userLogin);
	}

	@Override
	public void checkPassword(PerunSession sess, String userLogin, String password) {
		if (StringUtils.isBlank(password)) {
			throw new EmptyPasswordRuntimeException("Password for " + actualLoginNamespace + ":" + userLogin + " cannot be empty.");
		}
		Process process = createPwdManagerProcess(PASSWORD_CHECK, actualLoginNamespace, userLogin);
		sendPassword(process, password);
		handleExit(process, actualLoginNamespace, userLogin);
	}

	@Override
	public void changePassword(PerunSession sess, String userLogin, String newPassword) throws InternalErrorException {
		if (StringUtils.isBlank(newPassword)) {
			throw new EmptyPasswordRuntimeException("Password for " + actualLoginNamespace + ":" + userLogin + " cannot be empty.");
		}
		Process process = createPwdManagerProcess(PASSWORD_CHANGE, actualLoginNamespace, userLogin);
		sendPassword(process, newPassword);
		handleExit(process, actualLoginNamespace, userLogin);
	}

	@Override
	public void validatePassword(PerunSession sess, String userLogin) {
		Process process = createPwdManagerProcess(PASSWORD_VALIDATE, actualLoginNamespace, userLogin);
		handleExit(process, actualLoginNamespace, userLogin);
	}

	@Override
	public void deletePassword(PerunSession sess, String userLogin) throws InternalErrorException {
		Process process = createPwdManagerProcess(PASSWORD_DELETE, actualLoginNamespace, userLogin);
		handleExit(process, actualLoginNamespace, userLogin);
	}

	@Override
	public void createAlternativePassword(PerunSession sess, User user, String passwordId, String password) {
		if (StringUtils.isBlank(password)) {
			throw new EmptyPasswordRuntimeException("Password for " + actualLoginNamespace + ":" + passwordId + " cannot be empty.");
		}
		Process process = createAltPwdManagerProcess(PASSWORD_CREATE, actualLoginNamespace, user, passwordId);
		sendPassword(process, password);
		handleAltPwdManagerExit(process, user, actualLoginNamespace, passwordId);
	}

	@Override
	public void deleteAlternativePassword(PerunSession sess, User user, String passwordId) {
		Process process = createAltPwdManagerProcess(PASSWORD_DELETE, actualLoginNamespace, user, passwordId);
		handleAltPwdManagerExit(process, user, actualLoginNamespace, passwordId);
	}

	/**
	 * Run password manager script on path defined in perun config.
	 *
	 * @param operation Operation to perform (reserve, reserveRandom, validate, check, change, delete)
	 * @param loginNamespace Namespace in which operation is performed.
	 * @param login Login to perform operation for
	 * @return Started process
	 */
	protected Process createPwdManagerProcess(String operation, String loginNamespace, String login) {

		ProcessBuilder pb = new ProcessBuilder(passwordManagerProgram, operation, loginNamespace, login);

		Process process;
		try {
			process = pb.start();
		} catch (IOException e) {
			throw new InternalErrorException(e);
		}

		return process;

	}

	/**
	 * Send password to the STDIN of running password manager script process.
	 *
	 * @param process process waiting for password on STDIN
	 * @param password password to be set
	 */
	protected void sendPassword(Process process, String password) {

		OutputStream os = process.getOutputStream();
		// Write password to the stdin of the program
		PrintWriter pw = new PrintWriter(os, true);
		pw.write(password);
		pw.close();

	}

	/**
	 * Wait for password manager script to end and handle known return codes.
	 *
	 * @param process Running password manager script process.
	 * @param loginNamespace Namespace in which operation was performed.
	 * @param userLogin Login for which operation was performed.
	 */
	protected void handleExit(Process process, String loginNamespace, String userLogin) {

		InputStream es = process.getErrorStream();

		// If non-zero exit code is returned, then try to read error output
		try {
			if (process.waitFor() != 0) {
				if (process.exitValue() == 1) {
					throw new PasswordDoesntMatchRuntimeException("Old password doesn't match for " + loginNamespace + ":" + userLogin + ".");
				} else if (process.exitValue() == 3) {
					throw new PasswordChangeFailedRuntimeException("Password change failed for " + loginNamespace + ":" + userLogin + ".");
				} else if (process.exitValue() == 4) {
					throw new PasswordCreationFailedRuntimeException("Password creation failed for " + loginNamespace + ":" + userLogin + ".");
				} else if (process.exitValue() == 5) {
					throw new PasswordDeletionFailedRuntimeException("Password deletion failed for " + loginNamespace + ":" + userLogin + ".");
				} else if (process.exitValue() == 6) {
					throw new LoginNotExistsRuntimeException("User login doesn't exists in underlying system for " + loginNamespace + ":" + userLogin + ".");
				} else if (process.exitValue() == 11) {
					throw new PasswordStrengthFailedRuntimeException("Password to set doesn't match expected restrictions for " + loginNamespace + ":" + userLogin + ".");
				} else if (process.exitValue() == 12) {
					throw new PasswordOperationTimeoutRuntimeException("Operation with password exceeded expected limit for " + loginNamespace + ":" + userLogin + ".");
				} else {
					handleGenericErrorCode(es);
				}
			}
		} catch (InterruptedException e) {
			throw new InternalErrorException(e);
		}

	}

	protected Process createAltPwdManagerProcess(String operation, String loginNamespace, User user, String passwordId) {

		ProcessBuilder pb = new ProcessBuilder(altPasswordManagerProgram, operation, loginNamespace, Integer.toString(user.getId()), passwordId);

		Process process;
		try {
			process = pb.start();
		} catch (IOException e) {
			throw new InternalErrorException(e);
		}

		return process;

	}

	/**
	 * Wait for alternative password manager script to end and handle known return codes.
	 *
	 * @param process Running password manager script process.
	 * @param user User for which operation was performed.
	 * @param loginNamespace Namespace in which operation was performed.
	 * @param passwordId ID of alt password entry for which it was performed.
	 */
	protected void handleAltPwdManagerExit(Process process, User user, String loginNamespace, String passwordId) {

		InputStream es = process.getErrorStream();

		// If non-zero exit code is returned, then try to read error output
		try {
			if (process.waitFor() != 0) {
				if (process.exitValue() == 1) {
					//throw new PasswordDoesntMatchRuntimeException("Old password doesn't match for " + loginNamespace + ":" + userLogin + ".");
					throw new InternalErrorException("Alternative password manager returns unexpected return code: " + process.exitValue());
				} else if (process.exitValue() == 3) {
					//throw new PasswordChangeFailedRuntimeException("Password change failed for " + loginNamespace + ":" + userLogin + ".");
					throw new InternalErrorException("Alternative password manager returns unexpected return code: " + process.exitValue());
				} else if (process.exitValue() == 4) {
					throw new PasswordCreationFailedRuntimeException("Alternative password creation failed for " + user + ". Namespace: " + loginNamespace + ", passwordId: " + passwordId + ".");
				} else if (process.exitValue() == 5) {
					throw new PasswordDeletionFailedRuntimeException("Password deletion failed for " + user + ". Namespace: " + loginNamespace + ", passwordId: " + passwordId + ".");
				} else if (process.exitValue() == 6) {
					throw new LoginNotExistsRuntimeException("User doesn't exists in underlying system for namespace " + loginNamespace + ", user: " + user + ".");
				} else if (process.exitValue() == 7) {
					throw new InternalErrorException("Problem with creating user entry in underlying system " + loginNamespace + ", user: " + user + ".");
				} else {
					handleGenericErrorCode(es);
				}
			}
		} catch (InterruptedException e) {
			throw new InternalErrorException(e);
		}

	}

	/**
	 * Handle error stream from password manager script on unexpected return code.
	 *
	 * @param errorStream Password manager script error stream
	 */
	protected void handleGenericErrorCode(InputStream errorStream) {

		BufferedReader inReader = new BufferedReader(new InputStreamReader(errorStream));
		StringBuilder errorMsg = new StringBuilder();
		String line;
		try {
			while ((line = inReader.readLine()) != null) {
				errorMsg.append(line);
			}
		} catch (IOException e) {
			throw new InternalErrorException(e);
		}

		throw new InternalErrorException(errorMsg.toString());

	}

}
