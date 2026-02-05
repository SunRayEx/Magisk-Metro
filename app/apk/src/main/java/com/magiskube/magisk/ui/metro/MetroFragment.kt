package com.magiskube.magisk.ui.metro

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.magiskube.magisk.MainDirections
import com.magiskube.magisk.R
import com.magiskube.magisk.arch.BaseFragment
import com.magiskube.magisk.arch.ViewEvent
import com.magiskube.magisk.arch.viewModel
import com.magiskube.magisk.databinding.FragmentMetroBinding
import com.magiskube.magisk.ui.metro.MetroViewModel.CloseMenuEvent
import com.magiskube.magisk.core.R as CoreR

class MetroFragment : BaseFragment<FragmentMetroBinding>() {

    override val layoutRes = R.layout.fragment_metro
    override val viewModel by viewModel<MetroViewModel>()

    private var isMenuVisible = false
    private var currentMenuType: MetroViewModel.TileType? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTileClickListeners()
        setupGlobalLayoutListener()
    }

    override fun onEventDispatched(event: ViewEvent) {
        super.onEventDispatched(event)
        if (event is CloseMenuEvent) {
            hideMenu()
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(CoreR.string.section_home)
    }

    private fun setupTileClickListeners() {
        binding.apply {
            magiskTile.root.setOnClickListener {
                onTileClicked(MetroViewModel.TileType.MAGISK, magiskTile.root)
            }
            rootedTile.root.setOnClickListener {
                onTileClicked(MetroViewModel.TileType.ROOTED, rootedTile.root)
            }
            excludedTile.root.setOnClickListener {
                onTileClicked(MetroViewModel.TileType.EXCLUDED, excludedTile.root)
            }
            modulesTile.root.setOnClickListener {
                onTileClicked(MetroViewModel.TileType.MODULES, modulesTile.root)
            }
            contributorsTile.root.setOnClickListener {
                onTileClicked(MetroViewModel.TileType.CONTRIBUTORS, contributorsTile.root)
            }
            logsTile.root.setOnClickListener {
                onTileClicked(MetroViewModel.TileType.LOGS, logsTile.root)
            }
        }
    }

    private fun onTileClicked(tileType: MetroViewModel.TileType, tileView: View) {
        if (isMenuVisible && currentMenuType == tileType) {
            hideMenu()
        } else {
            showMenu(tileType, tileView)
        }
    }

    private fun showMenu(tileType: MetroViewModel.TileType, tileView: View) {
        isMenuVisible = true
        currentMenuType = tileType

        val flipOut = AnimatorInflater.loadAnimator(requireContext(), R.anim.tile_flip_out)
        flipOut.setTarget(tileView)
        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                loadSecondaryMenu(tileType)
                tileView.visibility = View.GONE
                binding.secondaryMenuContainer.visibility = View.VISIBLE

                val flipIn = AnimatorInflater.loadAnimator(requireContext(), R.anim.tile_flip_in)
                flipIn.setTarget(binding.secondaryMenuContainer)
                flipIn.start()
            }
        })
        flipOut.start()
    }

    private fun hideMenu() {
        currentMenuType?.let { tileType ->
            val tileView = when (tileType) {
                MetroViewModel.TileType.MAGISK -> binding.magiskTile.root
                MetroViewModel.TileType.ROOTED -> binding.rootedTile.root
                MetroViewModel.TileType.EXCLUDED -> binding.excludedTile.root
                MetroViewModel.TileType.MODULES -> binding.modulesTile.root
                MetroViewModel.TileType.CONTRIBUTORS -> binding.contributorsTile.root
                MetroViewModel.TileType.LOGS -> binding.logsTile.root
            }

            val flipOutReverse = AnimatorInflater.loadAnimator(requireContext(), R.anim.tile_flip_out_reverse)
            flipOutReverse.setTarget(binding.secondaryMenuContainer)
            flipOutReverse.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.secondaryMenuContainer.visibility = View.GONE
                    tileView.visibility = View.VISIBLE
                    binding.secondaryMenuContainer.removeAllViews()

                    val flipInReverse = AnimatorInflater.loadAnimator(requireContext(), R.anim.tile_flip_in_reverse)
                    flipInReverse.setTarget(tileView)
                    flipInReverse.start()

                    isMenuVisible = false
                    currentMenuType = null
                }
            })
            flipOutReverse.start()
        }
    }

    private fun loadSecondaryMenu(tileType: MetroViewModel.TileType) {
        binding.secondaryMenuContainer.removeAllViews()

        val menuLayout = when (tileType) {
            MetroViewModel.TileType.MAGISK -> R.layout.menu_metro_magisk
            MetroViewModel.TileType.ROOTED -> R.layout.menu_metro_rooted
            MetroViewModel.TileType.EXCLUDED -> R.layout.menu_metro_excluded
            MetroViewModel.TileType.MODULES -> R.layout.menu_metro_modules
            MetroViewModel.TileType.CONTRIBUTORS -> R.layout.menu_metro_contributors
            MetroViewModel.TileType.LOGS -> R.layout.menu_metro_logs
        }

        val menuView = LayoutInflater.from(requireContext())
            .inflate(menuLayout, binding.secondaryMenuContainer, false)
        binding.secondaryMenuContainer.addView(menuView)
    }

    private fun setupGlobalLayoutListener() {
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    override fun onBackPressed(): Boolean {
        return if (isMenuVisible) {
            hideMenu()
            true
        } else {
            false
        }
    }
}
