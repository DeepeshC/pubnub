#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <openssl/evp.h>
#include <openssl/bio.h>
#include <openssl/buffer.h>
#include "json.h"
#include "json_object.h"
#include <stddef.h>
#include "openssl/md5.h"

#define json_object_object_foreach(obj,key,val) \
        char *key; struct json_object *val; struct lh_entry *entry;\
        for(entry = json_object_get_object(obj)->head; ({ if(entry) { key = (char*)entry->k; val = (struct json_object*)entry->v; } ; entry; }); entry = entry->next )

#define BUFFFERLEN 5000
unsigned char md5Result[MD5_DIGEST_LENGTH];

char* base64(const unsigned char* input, int length) {
    BIO *bmem, *b64;
    BUF_MEM *bptr;

    b64 = BIO_new(BIO_f_base64());
    bmem = BIO_new(BIO_s_mem());
    b64 = BIO_push(b64, bmem);
    BIO_write(b64, input, length);
    BIO_flush(b64);
    BIO_get_mem_ptr(b64, &bptr);
    char *buff = (char *) malloc(bptr->length + 1);

    memcpy(buff, bptr->data, bptr->length);
    buff[bptr->length] = 0;

    BIO_free_all(b64);
    return buff;
}

char* unbase64(unsigned char* input, int length) {
    BIO *b64, *bmem;
    unsigned char *buffer = (unsigned char *) malloc(512);
    memset(buffer, 0, length);

    b64 = BIO_new(BIO_f_base64());
    bmem = BIO_new_mem_buf(input, length);
    bmem = BIO_push(b64, bmem);
    BIO_read(bmem, buffer, length);

    BIO_free_all(bmem);
    return buffer;
}

void getMD5(char* passward, unsigned char* returnVal) //returnVal must be global
{
    MD5((unsigned char*) passward, sizeof(passward), returnVal);
}

char* substring(const char* str, size_t begin, size_t len) {
    if (str == 0 || strlen(str) == 0 || strlen(str) < begin
            || strlen(str) < (begin + len))
        return 0;

    return strndup(str + begin, len);
}

char* rtrim(char *s) {
    char* back = s + strlen(s);
    while (isspace(*--back))
        ;
    *(back + 1) = '\0';
    return s;
}

char* getStringForDecrypt(char* input) {
    char* temp = malloc(strlen(input));
    strcpy(temp, input);

    int len = strlen(temp);
    if (len > 0)
        temp++; //Go past the first char
    //input[len - 4]='\n';
    if (len > 1)
        temp[len - 2] = '\0'; //Replace the last char with a null termination

    return temp;
}

char* encryptString(unsigned char* key, char* input) {
    unsigned char* iv = "0123456789012345";
    getMD5(key, md5Result);
    char* string_to_encrypt = input;
    int in_len = strlen(input);
    int dev = in_len % 8;
    int ans = dev;
    string_to_encrypt = malloc(ans + in_len + 1);
    int i;
    for (i = 0; i < in_len; i++) {
        string_to_encrypt[i] = input[i];
    }
    int j = in_len;
    for (i = 0; i < ans; i++) {
        string_to_encrypt[j] = ' ';
        j++;
    }
    string_to_encrypt[ans + in_len] = '\0';

    EVP_CIPHER_CTX en;
    EVP_CIPHER_CTX_init(&en);
    const EVP_CIPHER *cipher_type;
    unsigned char *ciphertext = NULL;
    int input_len = 0;
    cipher_type = EVP_aes_128_cbc();
    EVP_EncryptInit_ex(&en, cipher_type, NULL, md5Result, iv);
    static const int MAX_PADDING_LEN = 16;
    input_len = strlen(string_to_encrypt) + 1;
    ciphertext = (unsigned char *) malloc(input_len + MAX_PADDING_LEN);

    if (!EVP_EncryptInit_ex(&en, NULL, NULL, NULL, NULL)) {
        return "ERROR in EVP_EncryptInit_ex";
    }

    int bytes_written = 0;
    if (!EVP_EncryptUpdate(&en, ciphertext, &bytes_written,
            (unsigned char *) string_to_encrypt, input_len)) {
        return "ERROR in EVP_EncryptUpdate";
    }
    if (!EVP_EncryptFinal_ex(&en, ciphertext + bytes_written, &bytes_written)) {
        return "ERROR in EVP_EncryptFinal_ex";
    }
    EVP_CIPHER_CTX_cleanup(&en);

    char* base64_cipher = base64(ciphertext, strlen(ciphertext));
    strip(base64_cipher);
    return base64_cipher;
}

char* decryptString(unsigned char* key, char* encrypted) {
    unsigned char* plaintext = NULL;
    unsigned char* iv = "0123456789012345";
    char* ciphertext = malloc(strlen(encrypted));
    strip(encrypted);
    encrypted = strconcat(encrypted, "\n");
    ciphertext = unbase64(encrypted, strlen(encrypted));
    getMD5(key, md5Result);
    int bytes_written = 0;
    EVP_CIPHER_CTX de;
    EVP_CIPHER_CTX_init(&de);
    const EVP_CIPHER *cipher_type;
    cipher_type = EVP_aes_128_cbc();
    EVP_DecryptInit_ex(&de, cipher_type, NULL, md5Result, iv);
    plaintext = (unsigned char *) malloc(strlen(ciphertext));
    if (!EVP_DecryptInit_ex(&de, NULL, NULL, NULL, NULL)) {
        return "ERROR in EVP_DecryptInit_ex";
    }

    int plaintext_len = 0;
    if (!EVP_DecryptUpdate(&de, plaintext, &bytes_written, ciphertext,
            strlen(ciphertext))) {
        return "ERROR in EVP_DecryptUpdate";
    }
    plaintext_len += bytes_written;

    if (!EVP_DecryptFinal_ex(&de, plaintext + bytes_written, &bytes_written)) {
        return "ERROR in EVP_DecryptFinal_ex";
    }
    plaintext_len += bytes_written;

    EVP_CIPHER_CTX_cleanup(&de);

    char *ret;
    if (plaintext_len < strlen(plaintext)) {
        ret = substring(plaintext, 0, plaintext_len);
    } else {
        ret = plaintext;
    }
    return rtrim(ret);
}

json_object* encryptJSONObject(unsigned char* key, json_object* input) {
    json_object * my_object = json_object_new_object();

    json_object_object_foreach(input, jkey, jval) {
        char* encrypt = encryptString((unsigned char*) key,
                json_object_to_json_string(jval));
        json_object_object_add(my_object, jkey,
                json_object_new_string(encrypt));
    }
    return my_object;
}

json_object* decryptJSONObject(unsigned char* key, json_object* input) {
    json_object * my_object = json_object_new_object();

    json_object_object_foreach(input, jkey, jval) {
        char* temp = getStringForDecrypt(json_object_to_json_string(jval));
        char* decrypt = decryptString(key, temp); // getAllButFirstAndLast(json_object_to_json_string(val)));
        json_object_object_add(my_object, jkey,
                json_object_new_string(decrypt));
    }
    return my_object;
}

json_object* encryptJSONArray(unsigned char* key, json_object* input) {
    json_object * my_object = json_object_new_array();
    int i = 0;

    for (i = 0; i < json_object_array_length(input); i++) {
        json_object *obj = json_object_array_get_idx(input, i);
        char* encrypt = encryptString((unsigned char*) key,
                json_object_to_json_string(obj));
        json_object_array_add(my_object, json_object_new_string(encrypt));
    }
    return my_object;
}

json_object* decryptJSONArray(unsigned char* key, json_object* input) {
    json_object * my_object = json_object_new_array();
    int i = 0;

    for (i = 0; i < json_object_array_length(input); i++) {
        json_object *obj = json_object_array_get_idx(input, i);
        char* temp = getStringForDecrypt(json_object_to_json_string(obj));
        char* encrypt = decryptString(key, temp);
        json_object_array_add(my_object, json_object_new_string(encrypt));
    }
    return my_object;
}

json_object* decrypt(char* key, json_object* msg) {
    enum json_type type;
    json_object* encrypted = json_object_new_object();
    json_object* newMessage;

    if (json_object_array_length(msg) > 0) {
        newMessage = json_object_array_get_idx(msg, 0);
        char * encrypt;
        char* temp;
        type = json_object_get_type(newMessage);
        switch (type) {

        case json_type_object:
            encrypted = decryptJSONObject(key, newMessage);
            break;
        case json_type_array:
            encrypted = decryptJSONArray(key, newMessage);
            break;
        case json_type_string:
            temp = getStringForDecrypt(json_object_to_json_string(newMessage));
            char* test = strconcat(temp, "");
            encrypt = decryptString(key, test);
            encrypted = json_object_new_string(encrypt);
            break;
        }
    }
    return encrypted;
}

json_object* decryptHistry(char* key, json_object* msg) {
    int i = 0;
    char* temp;
    char* encrypt;
    json_object* my_array = json_object_new_array();
    json_object* encrypted = json_object_new_object();

    for (i = 0; i < json_object_array_length(msg); i++) {
        json_object *obj = json_object_array_get_idx(msg, i);

        enum json_type type;
        type = json_object_get_type(obj);
        switch (type) {

        case json_type_object:
            encrypted = decryptJSONObject(key, obj);
            break;
        case json_type_array:
            encrypted = decryptJSONArray(key, obj);
            break;
//        case json_type_string:
//            temp = getStringForDecrypt(json_object_to_json_string(obj));
//            char* test = strconcat(temp, "");
//            encrypt = decryptString(key, test);
//            encrypted = json_object_new_string(encrypt);
//            break;
        }
        json_object_array_add(my_array, encrypted);
    }
    return my_array;
}
