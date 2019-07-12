package com.feelsokman.stickers.ui.fragments.host.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.feelsokman.stickers.contentprovider.model.StickerPack
import com.feelsokman.stickers.ui.SingleLiveEvent
import com.feelsokman.stickers.usecase.BaseDisposableUseCase
import com.feelsokman.stickers.usecase.StickerPackLoaderUseCase
import com.feelsokman.stickers.usecase.error.DataSourceError
import timber.log.Timber

class HostViewModel(private val stickerPackLoaderUseCase: StickerPackLoaderUseCase) : ViewModel() {

    val stickerData = MutableLiveData<ArrayList<StickerPack>>()
    val errorMessage = SingleLiveEvent<String>()

    override fun onCleared() {
        Timber.tag("NavigationLogger").d("HostViewModel cleared")
        super.onCleared()
    }

    fun loadStickers() {
        if (stickerData.value.isNullOrEmpty()) {
            stickerPackLoaderUseCase.loadStickerPacks(object : BaseDisposableUseCase.Callback<ArrayList<StickerPack>> {
                override fun onLoadingStarted() {
                    //
                }

                override fun onSuccess(result: ArrayList<StickerPack>) {
                    stickerData.postValue(result)
                    //
                }

                override fun onError(error: DataSourceError) {
                    errorMessage.value = error.errorMessage
                }
            })
        } else {
            Timber.d("StickerPacks already here")
        }
    }
}