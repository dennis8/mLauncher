package com.github.droidworksstudio.mlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.droidworksstudio.mlauncher.MainViewModel
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.data.AppModel
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.Constants.AppDrawerFlag
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.databinding.FragmentAppDrawerBinding
import com.github.droidworksstudio.mlauncher.helper.AppDetailsHelper.isSystemApp
import com.github.droidworksstudio.mlauncher.helper.Colors
import com.github.droidworksstudio.mlauncher.helper.getHexFontColor
import com.github.droidworksstudio.mlauncher.helper.hideKeyboard
import com.github.droidworksstudio.mlauncher.helper.openAppInfo
import com.github.droidworksstudio.mlauncher.helper.openSearch
import com.github.droidworksstudio.mlauncher.helper.searchCustomSearchEngine
import com.github.droidworksstudio.mlauncher.helper.searchOnPlayStore
import com.github.droidworksstudio.mlauncher.helper.showToastShort

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter

    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    // Instantiate Colors object
    private val colors = Colors()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        prefs = Prefs(requireContext())
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("RtlHardcoded")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Retrieve the letter key code from arguments
        val letterKeyCode = arguments?.getInt("letterKeyCode", -1)
        if (letterKeyCode != null && letterKeyCode != -1) {
            val letterToChar = convertKeyCodeToLetter(letterKeyCode)
            val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
            searchTextView.text = letterToChar.toString()
        }

        binding.mainLayout.setBackgroundColor(colors.background(requireContext(), prefs))

        val flagString = arguments?.getString("flag", AppDrawerFlag.LaunchApp.toString()) ?: AppDrawerFlag.LaunchApp.toString()
        val flag = AppDrawerFlag.valueOf(flagString)
        val n = arguments?.getInt("n", 0) ?: 0

        when (flag) {
            AppDrawerFlag.SetHomeApp,
            AppDrawerFlag.SetShortSwipeRight,
            AppDrawerFlag.SetShortSwipeLeft,
            AppDrawerFlag.SetShortSwipeUp,
            AppDrawerFlag.SetShortSwipeDown,
            AppDrawerFlag.SetClickClock,
            AppDrawerFlag.SetAppUsage,
            AppDrawerFlag.SetClickDate -> {
                binding.drawerButton.setOnClickListener {
                    findNavController().popBackStack()
                }
            }
            else -> {}
        }

        val viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        val gravity = when(Prefs(requireContext()).drawerAlignment) {
            Constants.Gravity.Left -> Gravity.LEFT
            Constants.Gravity.Center -> Gravity.CENTER
            Constants.Gravity.Right -> Gravity.RIGHT
        }

        val appAdapter = context?.let {
            AppDrawerAdapter(
                it,
                flag,
                gravity,
                appClickListener(viewModel, flag, n),
                appDeleteListener(),
                this.appRenameListener(),
                appShowHideListener(),
                appInfoListener()
            )
        }

        if (appAdapter != null) {
            adapter = appAdapter
        }

        val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
        if (searchTextView != null) searchTextView.gravity = gravity

        val textSize = prefs.appSize.toFloat()
        searchTextView.textSize = textSize

        if (appAdapter != null) {
            initViewModel(flag, viewModel, appAdapter)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = appAdapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())

        if (flag == AppDrawerFlag.HiddenApps) {
            val hiddenAppsHint = getString(R.string.hidden_apps)
            if (prefs.followAccentColors) {
                val fontColor = getHexFontColor(requireActivity(), prefs)
                val coloredHint = SpannableString(hiddenAppsHint)
                coloredHint.setSpan(
                    ForegroundColorSpan(fontColor),
                    0,
                    hiddenAppsHint.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                binding.search.queryHint = coloredHint
            } else {
                val fontColor = colors.accents(requireContext(), prefs, 4)
                val coloredHint = SpannableString(hiddenAppsHint)
                coloredHint.setSpan(
                    ForegroundColorSpan(fontColor),
                    0,
                    hiddenAppsHint.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.search.queryHint = coloredHint
            }
        }
        if (flag == AppDrawerFlag.SetHomeApp) {
            val hiddenAppsHint = getString(R.string.please_select_app)
            if (prefs.followAccentColors) {
                val fontColor = getHexFontColor(requireActivity(), prefs)
                val coloredHint = SpannableString(hiddenAppsHint)
                coloredHint.setSpan(
                    ForegroundColorSpan(fontColor),
                    0,
                    hiddenAppsHint.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                binding.search.queryHint = coloredHint
            } else {
                val fontColor = colors.accents(requireContext(), prefs, 4)
                val coloredHint = SpannableString(hiddenAppsHint)
                coloredHint.setSpan(
                    ForegroundColorSpan(fontColor),
                    0,
                    hiddenAppsHint.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.search.queryHint = coloredHint
            }
        }
        if (flag == AppDrawerFlag.LaunchApp && prefs.useAllAppsText) {
            val allAppsHint = getString(R.string.show_apps)
            if (prefs.followAccentColors) {
                val fontColor = getHexFontColor(requireActivity(), prefs)
                val coloredHint = SpannableString(allAppsHint)
                coloredHint.setSpan(
                    ForegroundColorSpan(fontColor),
                    0,
                    allAppsHint.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                binding.search.queryHint = coloredHint
            } else {
                val fontColor = colors.accents(requireContext(), prefs, 4)
                val coloredHint = SpannableString(allAppsHint)
                coloredHint.setSpan(
                    ForegroundColorSpan(fontColor),
                    0,
                    allAppsHint.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.search.queryHint = coloredHint
            }
        }

        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                var searchQuery = query

                if (!searchQuery.isNullOrEmpty()) {
                    when {
                        searchQuery.startsWith("!") -> {
                            searchQuery = query?.substringAfter("!")
                            requireContext().searchCustomSearchEngine(searchQuery, prefs)
                        }
                        else -> {
                            // Handle unsupported search engines or invalid queries
                            if (adapter.itemCount == 0 && requireContext().searchOnPlayStore(query?.trim()).not()) {
                                requireContext().openSearch(query?.trim())
                            } else {
                                adapter.launchFirstInList()
                            }
                            return true // Exit the function
                        }
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (flag == AppDrawerFlag.SetHomeApp) {
                    binding.drawerButton.apply {
                        isVisible = !newText.isNullOrEmpty()
                        text = if (isVisible) getString(R.string.rename) else null
                        setOnClickListener { if (isVisible) renameListener(flag, n) }
                    }
                }
                newText?.let {
                    appAdapter?.filter?.filter(it.trim())
                }
                return false
            }

        })

    }

    private fun convertKeyCodeToLetter(keyCode: Int): Char {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> 'A'
            KeyEvent.KEYCODE_B -> 'B'
            KeyEvent.KEYCODE_C -> 'C'
            KeyEvent.KEYCODE_D -> 'D'
            KeyEvent.KEYCODE_E -> 'E'
            KeyEvent.KEYCODE_F -> 'F'
            KeyEvent.KEYCODE_G -> 'G'
            KeyEvent.KEYCODE_H -> 'H'
            KeyEvent.KEYCODE_I -> 'I'
            KeyEvent.KEYCODE_J -> 'J'
            KeyEvent.KEYCODE_K -> 'K'
            KeyEvent.KEYCODE_L -> 'L'
            KeyEvent.KEYCODE_M -> 'M'
            KeyEvent.KEYCODE_N -> 'N'
            KeyEvent.KEYCODE_O -> 'O'
            KeyEvent.KEYCODE_P -> 'P'
            KeyEvent.KEYCODE_Q -> 'Q'
            KeyEvent.KEYCODE_R -> 'R'
            KeyEvent.KEYCODE_S -> 'S'
            KeyEvent.KEYCODE_T -> 'T'
            KeyEvent.KEYCODE_U -> 'U'
            KeyEvent.KEYCODE_V -> 'V'
            KeyEvent.KEYCODE_W -> 'W'
            KeyEvent.KEYCODE_X -> 'X'
            KeyEvent.KEYCODE_Y -> 'Y'
            KeyEvent.KEYCODE_Z -> 'Z'
            else -> throw IllegalArgumentException("Invalid key code: $keyCode")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onResume() {
        super.onResume()
        binding.mainLayout.setBackgroundColor(colors.background(requireContext(), prefs))
    }

    private fun initViewModel(flag: AppDrawerFlag, viewModel: MainViewModel, appAdapter: AppDrawerAdapter) {
        viewModel.hiddenApps.observe(viewLifecycleOwner, Observer {
            if (flag != AppDrawerFlag.HiddenApps) return@Observer
            it?.let { appList ->
                binding.listEmptyHint.visibility = if (appList.isEmpty()) View.VISIBLE else View.GONE
                populateAppList(appList, appAdapter)
            }
        })

        viewModel.appList.observe(viewLifecycleOwner, Observer {
            if (flag == AppDrawerFlag.HiddenApps) return@Observer
            if (it == appAdapter.appsList) return@Observer
            it?.let { appList ->
                binding.listEmptyHint.visibility = if (appList.isEmpty()) View.VISIBLE else View.GONE
                populateAppList(appList, appAdapter)
            }
        })

        viewModel.firstOpen.observe(viewLifecycleOwner) {
            if (it) binding.appDrawerTip.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        binding.search.showKeyboard()
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    private fun View.showKeyboard() {
        if (!Prefs(requireContext()).autoShowKeyboard) return

        val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
        searchTextView.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        searchTextView.postDelayed({
            searchTextView.requestFocus()
            @Suppress("DEPRECATION")
            imm.showSoftInput(searchTextView, InputMethodManager.SHOW_FORCED)
        }, 100)
    }

    private fun populateAppList(apps: List<AppModel>, appAdapter: AppDrawerAdapter) {
        val animation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
        binding.recyclerView.layoutAnimation = animation
        appAdapter.setAppList(apps.toMutableList())
    }

    private fun appClickListener(viewModel: MainViewModel, flag: AppDrawerFlag, n: Int = 0): (appModel: AppModel) -> Unit =
        { appModel ->
            viewModel.selectedApp(appModel, flag, n)
            if (flag == AppDrawerFlag.LaunchApp || flag == AppDrawerFlag.HiddenApps)
                findNavController().popBackStack(R.id.mainFragment, false)
            else
                findNavController().popBackStack()
        }
    private fun appDeleteListener(): (appModel: AppModel) -> Unit =
        { appModel ->
            if (requireContext().isSystemApp(appModel.appPackage))
                showToastShort(requireContext(),getString(R.string.can_not_delete_system_apps))
            else {
                val appPackage = appModel.appPackage
                val intent = Intent(Intent.ACTION_DELETE)
                intent.data = Uri.parse("package:$appPackage")
                requireContext().startActivity(intent)
            }

        }
    private fun appRenameListener(): (appPackage: String, appAlias: String) -> Unit =
        { appPackage, appAlias ->
            val prefs = Prefs(requireContext())
            prefs.setAppAlias(appPackage, appAlias)
            findNavController().popBackStack()
        }
    private fun renameListener(flag: AppDrawerFlag, i: Int) {
        val name = binding.search.query.toString().trim()
        if (name.isEmpty()) return
        if (flag == AppDrawerFlag.SetHomeApp) {
            Prefs(requireContext()).setHomeAppName(i, name)
        }

        findNavController().popBackStack()
    }

    private fun appShowHideListener(): (flag: AppDrawerFlag, appModel: AppModel) -> Unit =
        { flag, appModel ->
            val prefs = Prefs(requireContext())
            val newSet = mutableSetOf<String>()
            newSet.addAll(prefs.hiddenApps)

            if (flag == AppDrawerFlag.HiddenApps) {
                newSet.remove(appModel.appPackage) // for backward compatibility
                newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
            } else newSet.add(appModel.appPackage + "|" + appModel.user.toString())

            prefs.hiddenApps = newSet

            if (newSet.isEmpty()) findNavController().popBackStack()
        }

    private fun appInfoListener(): (appModel: AppModel) -> Unit =
        { appModel ->
            openAppInfo(
                requireContext(),
                appModel.user,
                appModel.appPackage
            )
            findNavController().popBackStack(R.id.mainFragment, false)
        }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop) binding.search.hideKeyboard()
                        if (onTop && !recyclerView.canScrollVertically(1))
                            findNavController().popBackStack()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1)) {
                            binding.search.hideKeyboard()
                        } else if (!recyclerView.canScrollVertically(-1)) {
                            if (onTop) findNavController().popBackStack()
                            else binding.search.showKeyboard()
                        }
                    }
                }
            }
        }
    }
}
