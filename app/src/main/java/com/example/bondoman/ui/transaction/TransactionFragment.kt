package com.example.bondoman.ui.transaction

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Geocoder.GeocodeListener
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import com.example.bondoman.R
import com.example.bondoman.core.data.Transaction
import com.example.bondoman.databinding.FragmentTransactionBinding
import com.example.bondoman.lib.transaction.TRANSACTION_TYPE


class TransactionFragment : Fragment(), LocationListener, GeocodeListener {
    private var transactionId = 0L
    private val viewModel: TransactionViewModel by viewModels()
    private var currentTransaction = Transaction("", "", 0L, 0L, "",0.0,0.0)

    private var _binding: FragmentTransactionBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var curLocation : Location

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentTransactionBinding.inflate(inflater, container, false)
        val view = binding.root
        observeViewModel()
//        getLocation()
        getRequest()
        geocoder = Geocoder(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.transaction_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                when (menuItem.itemId) {
                    R.id.deleteTransaction -> {
                        if(context != null && transactionId != 0L){
                            AlertDialog.Builder(context!!)
                                .setTitle("Delete Transaction")
                                .setMessage("Are you sure you want to delete this transaction?")
                                .setPositiveButton("Yes") {dialogInterface ,i ->
                                    viewModel.deleteTransaction(currentTransaction)
                                }
                                .setNegativeButton("Cancel"){dialogInterface ,i ->}
                                .create()
                                .show()
                        }

                    }
                }
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

//        viewModel = ViewModelProvider(this)[TransactionViewModel::class.java]
        arguments?.let {
            transactionId = TransactionFragmentArgs.fromBundle(it).transactionID
            binding.titleView.setText(TransactionFragmentArgs.fromBundle(it).randomTitle)
        }

        if(transactionId != 0L){
            viewModel.getTransaction(transactionId)
        }
        binding.updateLocationButton.setOnClickListener{
            getLocation()
        }
        binding.pemasukanButton.setChecked(true)
        currentTransaction.type = TRANSACTION_TYPE.PEMASUKAN.toString()

        binding.pemasukanButton.setOnClickListener{
            currentTransaction.type = TRANSACTION_TYPE.PEMASUKAN.toString()
        }
        binding.pengeluaranButton.setOnClickListener {
            currentTransaction.type = TRANSACTION_TYPE.PEMBELIAN.toString()
        }
        binding.saveButton.setOnClickListener{
            Log.d("Observer", "Observer triggered with success")
            if(binding.titleView.text.toString() != "" && binding.nominalView.text.toString() != "") {
                val time:Long = System.currentTimeMillis()
                currentTransaction.location = binding.locationView.text.toString()
                if(this::curLocation.isInitialized) {
                    currentTransaction.latitude = curLocation.latitude
                    currentTransaction.longitude = curLocation.longitude
                }
                else{
                    currentTransaction.latitude = 0.0
                    currentTransaction.longitude = 0.0
                }
                currentTransaction.title =binding.titleView.text.toString()
                currentTransaction.nominal = binding.nominalView.text.toString().toLong()
                if(currentTransaction.id == 0L){
                    currentTransaction.creationTime = time
                }
                viewModel.saveTransaction(currentTransaction)

            }
            Navigation.findNavController(it).popBackStack()
        }
        binding.mapsButton.setOnClickListener{
            if(this::curLocation.isInitialized)
                openMapsIntent(curLocation.latitude, curLocation.longitude)
        }

    }

    // on below line creating an open maps intent method.
    private fun openMapsIntent(lat: Double, lng: Double) {
        val gmmIntentUri = Uri.parse("http://www.google.com/maps/place/${lat.toString()},${lng.toString()}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
//        mapIntent.setPackage("com.google.android.apps.maps")
//        mapIntent.resolveActivity(requireActivity().packageManager)?.let {
//            Log.d("aa", " triggered")
            startActivity(mapIntent)
//        }
    }
    private fun observeViewModel(){
        viewModel.saved.observe(viewLifecycleOwner, Observer<Boolean> {it->
            Log.d("Observer", "Observer triggered with yippie")
            if(it) {
                Toast.makeText(context, "Done!", Toast.LENGTH_SHORT).show()
                Navigation.findNavController(binding.titleView).popBackStack()
            } else{
                Toast.makeText(context, "Something went wrong, please try again!", Toast.LENGTH_SHORT).show()
            }
        }
        )

        viewModel.currentTransaction.observe(viewLifecycleOwner, Observer{transaction->
            transaction?.let {
                currentTransaction = it
                binding.titleView.setText(it.title, TextView.BufferType.EDITABLE)
                if(it.type == TRANSACTION_TYPE.PEMASUKAN.toString()){
                    binding.pemasukanButton.setChecked(true)
                }
                if(it.type == TRANSACTION_TYPE.PEMBELIAN.toString()){
                    binding.pengeluaranButton.setChecked(true)
                }
                binding.locationView.setText(it.location, TextView.BufferType.NORMAL)
                curLocation = Location(it.location)
                curLocation.latitude = it.latitude
                curLocation.longitude = it.longitude
                binding.nominalView.setText(it.nominal.toString(), TextView.BufferType.EDITABLE)
            }

        })
    }

    // location stuff
    private lateinit var locationManager: LocationManager
    private lateinit var tvGpsLocation: TextView
    private val locationPermissionCode = 2

    private fun getRequest(){
        locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Permission Granted lol?", Toast.LENGTH_SHORT).show()
//            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
        } else {
            Log.d("ples]aes","request pleasesaesee")
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
            Log.d("where","why not here")

            val locationPermissionRequest = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                when {
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                        // Precise location access granted.
                        Toast.makeText(context, "yippie!!!", Toast.LENGTH_SHORT).show()
                    }
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                        // Only approximate location access granted.
                    } else -> {
                    // No location access granted.
                    Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
                }
            }
        }
    }
    private fun getLocation() {
//        locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Permission Granted lol?", Toast.LENGTH_SHORT).show()
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
        } else {

        }

    }


    override fun onLocationChanged(location: Location) {
        curLocation = location
        tvGpsLocation = binding.locationView
        geocoder.getFromLocation(location.latitude,location.longitude,1, this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT).show()
            }
            else {
                Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // geocode stuff

    private lateinit var geocoder: Geocoder
    override fun onGeocode(p0: MutableList<Address>) {
        tvGpsLocation.text = p0[0].getAddressLine(0).toString()
    }


}