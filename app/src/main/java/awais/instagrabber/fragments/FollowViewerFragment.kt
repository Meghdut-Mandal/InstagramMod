package awais.instagrabber.fragments

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import java.util.ArrayList

import awais.instagrabber.R
import awais.instagrabber.adapters.FollowAdapter
import awais.instagrabber.customviews.helpers.RecyclerLazyLoader
import awais.instagrabber.databinding.FragmentFollowersViewerBinding
import awais.instagrabber.models.Resource
import awais.instagrabber.repositories.responses.User
import awais.instagrabber.utils.AppExecutors
import awais.instagrabber.viewmodels.FollowViewModel
import thoughtbot.expandableadapter.ExpandableGroup

class FollowViewerFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {
    private val followModels: ArrayList<User> = ArrayList<User>()
    private val followingModels: ArrayList<User> = ArrayList<User>()
    private val followersModels: ArrayList<User> = ArrayList<User>()
    private val allFollowing: ArrayList<User> = ArrayList<User>()
    private val moreAvailable = true
    private var isFollowersList = false
    private var isCompare = false
    private var shouldRefresh = true
    private var searching = false
    private var profileId: Long = 0
    private var username: String? = null
    private var namePost: String? = null
    private var type = 0
    private var root: SwipeRefreshLayout? = null
    private var adapter: FollowAdapter? = null
    private lateinit var lazyLoader: RecyclerLazyLoader
    private lateinit var fragmentActivity: AppCompatActivity
    private lateinit var viewModel: FollowViewModel
    private lateinit var binding: FragmentFollowersViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentActivity = activity as AppCompatActivity
        viewModel = ViewModelProvider(this).get(FollowViewModel::class.java)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (root != null) {
            shouldRefresh = false
            return root!!
        }
        binding = FragmentFollowersViewerBinding.inflate(layoutInflater)
        root = binding.root
        return root!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!shouldRefresh) return
        init()
        shouldRefresh = false
    }

    private fun init() {
        val args = arguments ?: return
        val fragmentArgs = FollowViewerFragmentArgs.fromBundle(args)
        viewModel.userId.value = fragmentArgs.profileId
        isFollowersList = fragmentArgs.isFollowersList
        username = fragmentArgs.username
        namePost = username
        setTitle(username)
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        if (isCompare) listCompare() else listFollows()
        viewModel.fetch(isFollowersList, null)
    }

    override fun onResume() {
        super.onResume()
        setTitle(username)
        setSubtitle(type)
    }

    private fun setTitle(title: String?) {
        val actionBar: ActionBar = fragmentActivity.supportActionBar ?: return
        actionBar.title = title
    }

    private fun setSubtitle(subtitleRes: Int) {
        val actionBar: ActionBar = fragmentActivity.supportActionBar ?: return
        actionBar.setSubtitle(subtitleRes)
    }

    override fun onRefresh() {
        lazyLoader.resetState()
        viewModel.clearProgress()
        if (isCompare) listCompare()
        else viewModel.fetch(isFollowersList, null)
    }

    private fun listFollows() {
        viewModel.comparison.removeObservers(viewLifecycleOwner)
        viewModel.status.removeObservers(viewLifecycleOwner)
        type = if (isFollowersList) R.string.followers_type_followers else R.string.followers_type_following
        setSubtitle(type)
        val layoutManager = LinearLayoutManager(context)
        lazyLoader = RecyclerLazyLoader(layoutManager, { _, totalItemsCount ->
            binding.swipeRefreshLayout.isRefreshing = true
            val liveData = if (searching) viewModel.search(isFollowersList)
                           else viewModel.fetch(isFollowersList, null)
            liveData.observe(viewLifecycleOwner) {
                binding.swipeRefreshLayout.isRefreshing = it.status != Resource.Status.SUCCESS
                layoutManager.scrollToPosition(totalItemsCount)
            }
        })
        binding.rvFollow.addOnScrollListener(lazyLoader)
        binding.rvFollow.layoutManager = layoutManager
        viewModel.getList(isFollowersList).observe(viewLifecycleOwner) {
            binding.swipeRefreshLayout.isRefreshing = false
            refreshAdapter(it, null, null, null)
        }
    }

    private fun listCompare() {
        viewModel.getList(isFollowersList).removeObservers(viewLifecycleOwner)
        binding.rvFollow.clearOnScrollListeners()
        binding.swipeRefreshLayout.isRefreshing = true
        setSubtitle(R.string.followers_compare)
        viewModel.status.observe(viewLifecycleOwner) {}
        viewModel.comparison.observe(viewLifecycleOwner) {
            if (it != null) {
                binding.swipeRefreshLayout.isRefreshing = false
                refreshAdapter(null, it.first, it.second, it.third)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.follow, menu)
        val menuSearch = menu.findItem(R.id.action_search)
        val searchView = menuSearch.actionView as SearchView
        searchView.queryHint = resources.getString(R.string.action_search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(query: String): Boolean {
                if (query.isNullOrEmpty()) {
                    if (!isCompare && searching) {
                        viewModel.setQuery(null, isFollowersList)
                        viewModel.getSearch().removeObservers(viewLifecycleOwner)
                        viewModel.getList(isFollowersList).observe(viewLifecycleOwner) {
                            refreshAdapter(it, null, null, null)
                        }
                    }
                    searching = false
                    return true
                }
                searching = true
                if (isCompare && adapter != null) {
                    adapter!!.filter.filter(query)
                    return true
                }
                viewModel.getList(isFollowersList).removeObservers(viewLifecycleOwner)
                binding.swipeRefreshLayout.isRefreshing = true
                viewModel.setQuery(query, isFollowersList)
                viewModel.getSearch().observe(viewLifecycleOwner) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    refreshAdapter(it, null, null, null)
                }
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_compare) return super.onOptionsItemSelected(item)
        binding.rvFollow.adapter = null
        if (isCompare) {
            isCompare = false
            listFollows()
        } else {
            isCompare = true
            listCompare()
        }
        return true
    }

    private fun refreshAdapter(
        followModels: List<User>?,
        allFollowing: List<User>?,
        followingModels: List<User>?,
        followersModels: List<User>?
    ) {
        val groups: ArrayList<ExpandableGroup> = ArrayList<ExpandableGroup>(1)
        if (isCompare && followingModels != null && followersModels != null && allFollowing != null) {
            if (followingModels.size > 0) groups.add(
                ExpandableGroup(
                    getString(
                        R.string.followers_not_following,
                        username
                    ), followingModels
                )
            )
            if (followersModels.size > 0) groups.add(
                ExpandableGroup(
                    getString(
                        R.string.followers_not_follower,
                        namePost
                    ), followersModels
                )
            )
            if (allFollowing.size > 0) groups.add(
                ExpandableGroup(
                    getString(R.string.followers_both_following),
                    allFollowing
                )
            )
        } else if (followModels != null) {
            groups.add(ExpandableGroup(getString(type), followModels))
        } else return
        adapter = FollowAdapter({ v ->
            val tag = v.tag
            if (tag is User) {
                val model = tag
                val bundle = Bundle()
                bundle.putString("username", model.username)
                NavHostFragment.findNavController(this).navigate(R.id.action_global_profileFragment, bundle)
            }
        }, groups)
        adapter!!.toggleGroup(0)
        binding.rvFollow.adapter = adapter!!
    }

    companion object {
        private const val TAG = "FollowViewerFragment"
    }
}