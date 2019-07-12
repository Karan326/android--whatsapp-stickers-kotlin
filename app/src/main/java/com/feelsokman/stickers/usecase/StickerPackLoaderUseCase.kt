package com.feelsokman.stickers.usecase

import android.content.ContentResolver
import android.database.Cursor
import com.feelsokman.stickers.contentprovider.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY
import com.feelsokman.stickers.contentprovider.IOS_APP_DOWNLOAD_LINK_IN_QUERY
import com.feelsokman.stickers.contentprovider.LICENSE_AGREEMENT_WEBSITE
import com.feelsokman.stickers.contentprovider.PRIVACY_POLICY_WEBSITE
import com.feelsokman.stickers.contentprovider.PUBLISHER_EMAIL
import com.feelsokman.stickers.contentprovider.PUBLISHER_WEBSITE
import com.feelsokman.stickers.contentprovider.STICKER_FILE_EMOJI_IN_QUERY
import com.feelsokman.stickers.contentprovider.STICKER_FILE_NAME_IN_QUERY
import com.feelsokman.stickers.contentprovider.STICKER_PACK_ICON_IN_QUERY
import com.feelsokman.stickers.contentprovider.STICKER_PACK_IDENTIFIER_IN_QUERY
import com.feelsokman.stickers.contentprovider.STICKER_PACK_NAME_IN_QUERY
import com.feelsokman.stickers.contentprovider.STICKER_PACK_PUBLISHER_IN_QUERY
import com.feelsokman.stickers.contentprovider.StickerProviderHelper
import com.feelsokman.stickers.contentprovider.model.Sticker
import com.feelsokman.stickers.contentprovider.model.StickerPack
import com.feelsokman.stickers.usecase.error.DataSourceError
import com.feelsokman.stickers.usecase.error.DataSourceErrorKind
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.util.ArrayList
import java.util.HashSet

class StickerPackLoaderUseCase(
    private val scheduler: Scheduler,
    private val stickerProviderHelper: StickerProviderHelper,
    private val fetchStickerAssetUseCase: FetchStickerAssetUseCase,
    private val uriResolverUseCase: UriResolverUseCase,
    private val stickerPackValidator: StickerPackValidator
) : BaseDisposableUseCase() {

    override val compositeDisposable: CompositeDisposable = CompositeDisposable()
    override var latestDisposable: Disposable? = null

    fun loadStickerPacks(callback: Callback<ArrayList<StickerPack>>) {
        latestDisposable?.dispose()
        latestDisposable =
            Single.fromCallable { fetchStickerPacks() }
                .subscribeOn(scheduler)
                .doOnSubscribe { compositeDisposable.add(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { stickerPacks -> callback.onSuccess(stickerPacks) },
                    { error -> callback.onError(DataSourceError(error.localizedMessage, DataSourceErrorKind.UNEXPECTED)) }
                )
    }

    @Throws(IllegalStateException::class)
    private fun fetchStickerPacks(): ArrayList<StickerPack> {
        var stickerPackList: ArrayList<StickerPack> = arrayListOf()
        try {
            stickerProviderHelper.contentResolver.query(uriResolverUseCase.getAuthorityUri(), null, null, null, null)?.use {
                stickerPackList = fetchFromContentProvider(it)
            }
        } catch (exception: Exception) {
            throw IllegalStateException("could not fetch from content provider $stickerProviderHelper.providerAuthority")
        }

        val identifierSet = HashSet<String>()

        for (stickerPack in stickerPackList) {
            if (identifierSet.contains(stickerPack.identifier)) {
                throw IllegalStateException("sticker pack identifiers should be unique, there are more than one pack with identifier:" + stickerPack.identifier)
            } else {
                identifierSet.add(stickerPack.identifier!!)
            }
        }
        if (stickerPackList.isEmpty()) {
            throw IllegalStateException("There should be at least one sticker pack in the app")
        }
        for (stickerPack in stickerPackList) {
            val stickers = getStickersForPack(stickerPack)
            stickerPack.resetStickers(stickers)
            stickerPackValidator.verifyStickerPackValidity(stickerPack)
        }
        return stickerPackList
    }

    private fun getStickersForPack(stickerPack: StickerPack): List<Sticker> {
        val stickers = fetchFromContentProviderForStickers(stickerPack.identifier!!, stickerProviderHelper.contentResolver)
        for (sticker in stickers) {
            try {
                val bytes: ByteArray =
                    fetchStickerAssetUseCase.fetchStickerAsset(stickerPack.identifier!!, sticker.imageFileName!!)
                if (bytes.isEmpty()) {
                    throw Exception("Asset file is empty, pack: " + stickerPack.name + ", sticker: " + sticker.imageFileName)
                }
                sticker.size = bytes.size.toLong()
            } catch (e: Throwable) {
                throw Exception("Asset file doesn't exist. pack: $stickerPack.name, sticker: $sticker.imageFileName, error: ${e.localizedMessage}")
            }
        }
        return stickers
    }

    private fun fetchFromContentProvider(cursor: Cursor): ArrayList<StickerPack> {
        val stickerPackList = ArrayList<StickerPack>()
        cursor.moveToFirst()
        do {
            val identifier = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_IDENTIFIER_IN_QUERY))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_NAME_IN_QUERY))
            val publisher = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_PUBLISHER_IN_QUERY))
            val trayImage = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_PACK_ICON_IN_QUERY))
            val androidPlayStoreLink = cursor.getString(cursor.getColumnIndexOrThrow(ANDROID_APP_DOWNLOAD_LINK_IN_QUERY))
            val iosAppLink = cursor.getString(cursor.getColumnIndexOrThrow(IOS_APP_DOWNLOAD_LINK_IN_QUERY))
            val publisherEmail = cursor.getString(cursor.getColumnIndexOrThrow(PUBLISHER_EMAIL))
            val publisherWebsite = cursor.getString(cursor.getColumnIndexOrThrow(PUBLISHER_WEBSITE))
            val privacyPolicyWebsite = cursor.getString(cursor.getColumnIndexOrThrow(PRIVACY_POLICY_WEBSITE))
            val licenseAgreementWebsite = cursor.getString(cursor.getColumnIndexOrThrow(LICENSE_AGREEMENT_WEBSITE))
            val stickerPack = StickerPack(
                identifier,
                name,
                publisher,
                trayImage,
                publisherEmail,
                publisherWebsite,
                privacyPolicyWebsite,
                licenseAgreementWebsite
            )
            stickerPack.androidPlayStoreLink = androidPlayStoreLink
            stickerPack.iosAppStoreLink = iosAppLink
            stickerPackList.add(stickerPack)
        } while (cursor.moveToNext())

        cursor.close()
        return stickerPackList
    }

    private fun fetchFromContentProviderForStickers(identifier: String, contentResolver: ContentResolver): List<Sticker> {
        val uri = uriResolverUseCase.getStickerListUri(identifier)

        val projection = arrayOf(STICKER_FILE_NAME_IN_QUERY, STICKER_FILE_EMOJI_IN_QUERY)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        val stickers = ArrayList<Sticker>()
        if (cursor != null && cursor.count > 0) {
            cursor.moveToFirst()
            do {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_FILE_NAME_IN_QUERY))
                val emojisConcatenated = cursor.getString(cursor.getColumnIndexOrThrow(STICKER_FILE_EMOJI_IN_QUERY))
                stickers.add(
                    Sticker(
                        name,
                        listOf(*emojisConcatenated.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor?.close()
        return stickers
    }

    override fun stopAllBackgroundWork() {
        compositeDisposable.clear()
    }
}