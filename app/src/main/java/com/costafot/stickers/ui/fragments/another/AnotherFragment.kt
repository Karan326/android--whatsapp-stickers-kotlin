package com.costafot.stickers.ui.fragments.another

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.costafot.stickers.R
import com.costafot.stickers.ui.activity.viewmodel.MainViewModel
import com.costafot.stickers.ui.base.BaseFragment
import com.costafot.stickers.ui.fragments.another.viewmodel.AnotherViewModel
import com.costafot.stickers.ui.fragments.another.viewmodel.AnotherViewModelFactory
import timber.log.Timber
import javax.inject.Inject

class AnotherFragment : BaseFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_another, container, false)
    }

    @Inject
    internal lateinit var factory: AnotherViewModelFactory

    private val viewModelAnother by viewModels<AnotherViewModel>({ this }, { factory })
    private val activityViewModel by activityViewModels<MainViewModel>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModelAnother.textData.observe(viewLifecycleOwner, Observer { stringFromStorage ->
            Timber.tag("NavigationLogger").e("AnotherFragment storage string is $stringFromStorage")
        })
    }
}