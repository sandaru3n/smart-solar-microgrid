package com.ead.solargrid.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ead.solargrid.models.User

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "SolarGridSession.db"
        
        private const val TABLE_USER = "session_user"
        private const val COLUMN_NIC = "nic"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PHONE = "phone"
        private const val COLUMN_ADDRESS = "address"
        private const val COLUMN_ROLE = "role"
        private const val COLUMN_STATUS = "account_status"
        private const val COLUMN_PROFILE_PIC = "profile_pic"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_USER + "("
                + COLUMN_NIC + " TEXT PRIMARY KEY,"
                + COLUMN_NAME + " TEXT,"
                + COLUMN_EMAIL + " TEXT,"
                + COLUMN_PHONE + " TEXT,"
                + COLUMN_ADDRESS + " TEXT,"
                + COLUMN_ROLE + " TEXT,"
                + COLUMN_STATUS + " TEXT,"
                + COLUMN_PROFILE_PIC + " TEXT" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER)
        onCreate(db)
    }

    fun saveUser(nic: String, name: String, email: String, phone: String, address: String, role: String, accountStatus: String, profilePicUrl: String?) {
        val db = this.writableDatabase
        db.delete(TABLE_USER, null, null) // Keep only one session
        
        val values = ContentValues()
        values.put(COLUMN_NIC, nic)
        values.put(COLUMN_NAME, name)
        values.put(COLUMN_EMAIL, email)
        values.put(COLUMN_PHONE, phone)
        values.put(COLUMN_ADDRESS, address)
        values.put(COLUMN_ROLE, role)
        values.put(COLUMN_STATUS, accountStatus)
        values.put(COLUMN_PROFILE_PIC, profilePicUrl)

        db.insert(TABLE_USER, null, values)
        db.close()
    }

    fun getUser(): User? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_USER", null)
        var user: User? = null
        if (cursor.moveToFirst()) {
            val roleStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROLE))
            // The API role is integer based (0: PROSUMER, etc.), but UI treats it dynamically or you can parse.
            val roleInt = if (roleStr == "BACKOFFICE") 0 else if (roleStr == "GRID_OPERATOR") 1 else 2
            
            user = User(
                id = null,
                nic = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NIC)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)),
                phone = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE)),
                address = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS)),
                role = roleStr,
                accountStatus = "ACTIVE",
                profilePicUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_PIC))
            )
        }
        cursor.close()
        db.close()
        return user
    }
    
    fun getUserRole(): String? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_ROLE FROM $TABLE_USER", null)
        var role: String? = null
        if (cursor.moveToFirst()) {
            role = cursor.getString(0)
        }
        cursor.close()
        db.close()
        return role
    }

    fun clearSession() {
        val db = this.writableDatabase
        db.delete(TABLE_USER, null, null)
        db.close()
    }
}
