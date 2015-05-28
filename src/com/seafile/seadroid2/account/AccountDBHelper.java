package com.seafile.seadroid2.account;

import java.util.List;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.data.ServerInfo;
import com.seafile.seadroid2.provider.AccountNotifier;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * A helper class to manage {@link #DATABASE_NAME} database creation and version management.
 */
public class AccountDBHelper extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "account.db";

    private static final String ACCOUNT_TABLE_NAME = "Account";
    private static final String SERVER_INFO_TABLE_NAME = "ServerInfo";

    // Account
    private static final String ACCOUNT_COLUMN_SERVER = "server";
    private static final String ACCOUNT_COLUMN_EMAIL = "email";
    private static final String ACCOUNT_COLUMN_TOKEN = "token";

    // Server info
    private static final String SERVER_INFO_COLUMN_URL = "url";
    private static final String SERVER_INFO_COLUMN_VERSION = "version";
    private static final String SERVER_INFO_COLUMN_FEATURE = "feature";

    private static final String SQL_CREATE_SERVER_INFO_TABLE =
            "CREATE TABLE " + SERVER_INFO_TABLE_NAME + " ("
                    + SERVER_INFO_COLUMN_URL + " VARCHAR(255) PRIMARY KEY, "
                    + SERVER_INFO_COLUMN_VERSION + " TEXT NOT NULL, "
                    + SERVER_INFO_COLUMN_FEATURE + " TEXT NOT NULL" + ")";

    private static final String SQL_CREATE_ACCOUNT_TABLE =
            "CREATE TABLE " + ACCOUNT_TABLE_NAME + " ("
                    + ACCOUNT_COLUMN_SERVER + " TEXT NOT NULL, "
                    + ACCOUNT_COLUMN_EMAIL + " TEXT NOT NULL, "
                    + ACCOUNT_COLUMN_TOKEN + " TEXT NOT NULL);";

    private static AccountDBHelper dbHelper = null;
    private SQLiteDatabase database = null;

    public static synchronized AccountDBHelper getDatabaseHelper(Context context) {
        // Note: the given context will be used for the singleton instance. it can come
        // either from the application or the contentProvider.
        if (dbHelper != null)
            return dbHelper;
        dbHelper = new AccountDBHelper(context);
        dbHelper.database = dbHelper.getWritableDatabase();
        AccountNotifier.notifyProvider();
        return dbHelper;
    }

    private AccountDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ACCOUNT_TABLE);
        db.execSQL(SQL_CREATE_SERVER_INFO_TABLE);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL("DROP TABLE IF EXISTS " + ACCOUNT_TABLE_NAME + ";");
        db.execSQL("DROP TABLE IF EXISTS " + SERVER_INFO_TABLE_NAME + ";");
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public Account getAccount(String server, String email) {
        String[] projection = {
                AccountDBHelper.ACCOUNT_COLUMN_SERVER,
                AccountDBHelper.ACCOUNT_COLUMN_EMAIL,
                AccountDBHelper.ACCOUNT_COLUMN_TOKEN
        };

        Cursor c = database.query(
             AccountDBHelper.ACCOUNT_TABLE_NAME,
             projection,
             "server=? and email=?",
             new String[] { server, email },
             null,   // don't group the rows
             null,   // don't filter by row groups
             null    // The sort order
         );

        if (!c.moveToFirst()) {
            c.close();
            return null;
        }

        Account account = cursorToAccount(c);
        c.close();
        return account;
    }

    public List<Account> getAccountList() {
        List<Account> accounts = Lists.newArrayList();

        String[] projection = {
                AccountDBHelper.ACCOUNT_COLUMN_SERVER,
                AccountDBHelper.ACCOUNT_COLUMN_EMAIL,
                AccountDBHelper.ACCOUNT_COLUMN_TOKEN
        };

        Cursor c = database.query(
             AccountDBHelper.ACCOUNT_TABLE_NAME,
             projection,
             null,
             null,
             null,   // don't group the rows
             null,   // don't filter by row groups
             null    // The sort order
        );

        c.moveToFirst();
        while (!c.isAfterLast()) {
            Account account = cursorToAccount(c);
            accounts.add(account);
            c.moveToNext();
        }

        c.close();
        return accounts;
    }

    public void saveAccount(Account account) {
        Account old = getAccount(account.server, account.email);
        if (old != null) {
            if (old.token.equals(account.token))
                return;
            else
                deleteAccount(old);
        }

        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(AccountDBHelper.ACCOUNT_COLUMN_SERVER, account.server);
        values.put(AccountDBHelper.ACCOUNT_COLUMN_EMAIL, account.email);
        values.put(AccountDBHelper.ACCOUNT_COLUMN_TOKEN, account.token);

        // Insert the new row, returning the primary key value of the new row
        database.replace(AccountDBHelper.ACCOUNT_TABLE_NAME, null, values);

        AccountNotifier.notifyProvider();
    }

    public void updateAccount(Account oldAccount, Account newAccount) {
        ContentValues values = new ContentValues();
        values.put(AccountDBHelper.ACCOUNT_COLUMN_SERVER, newAccount.server);
        values.put(AccountDBHelper.ACCOUNT_COLUMN_EMAIL, newAccount.email);
        values.put(AccountDBHelper.ACCOUNT_COLUMN_TOKEN, newAccount.token);

        database.update(AccountDBHelper.ACCOUNT_TABLE_NAME, values, "server=? and email=?",
                new String[]{oldAccount.server, oldAccount.email});

        AccountNotifier.notifyProvider();
    }

    public void deleteAccount(Account account) {
        database.delete(AccountDBHelper.ACCOUNT_TABLE_NAME,  "server=? and email=?",
                new String[] { account.server, account.email });

        AccountNotifier.notifyProvider();
    }

    private Account cursorToAccount(Cursor cursor) {
        Account account = new Account();
        account.server = cursor.getString(0);
        account.email = cursor.getString(1);
        account.token = cursor.getString(2);
        return account;
    }

    public ServerInfo getServerInfo(String url) {
        String[] projection = {SERVER_INFO_COLUMN_URL, SERVER_INFO_COLUMN_VERSION, SERVER_INFO_COLUMN_FEATURE};

        Cursor c = database.query(SERVER_INFO_TABLE_NAME,
                projection,
                "url=?",
                new String[] {url},
                null,  // don't group the rows
                null,  // don't filter by row groups
                null); // The sort order

        if (!c.moveToFirst()) {
            c.close();
            return null;
        }

        ServerInfo serverInfo = cursorToServerInfo(c);

        c.close();
        return serverInfo;
    }

    private ServerInfo cursorToServerInfo(Cursor cursor) {
        String url = cursor.getString(0);
        String version = cursor.getString(1);
        String features = cursor.getString(2);
        ServerInfo serverInfo = new ServerInfo(url, version, features);
        serverInfo.setProEdition(features.contains("seafile-pro"));
        return serverInfo;
    }

    public void saveServerInfo(ServerInfo serverInfo) {
        ContentValues values = new ContentValues();
        values.put(SERVER_INFO_COLUMN_URL, serverInfo.getUrl());
        values.put(SERVER_INFO_COLUMN_VERSION, serverInfo.getVersion());
        values.put(SERVER_INFO_COLUMN_FEATURE, serverInfo.getFeatures());

        database.replace(SERVER_INFO_TABLE_NAME, null, values);
    }
}
