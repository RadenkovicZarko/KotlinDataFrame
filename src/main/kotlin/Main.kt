import kotlinx.coroutines.*
import org.jetbrains.kotlinx.dataframe.*
import org.jetbrains.kotlinx.dataframe.api.*
import org.jsoup.Jsoup
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.RowFilter
import javax.swing.table.TableRowSorter


fun main() {
  val url = "https://en.wikipedia.org/wiki/List_of_countries_and_dependencies_by_population"
  val extractedData = runBlocking { parseWebsite(url) }
  val columns = extractedData.first().keys.toList()
  val rows = extractedData.map { it.values.toList() }
  val dataFrame = dataFrameOf(*columns.toTypedArray())(*rows.toTypedArray())
  SwingUtilities.invokeLater { visualizeDataFrame(dataFrame) }
}


suspend fun parseWebsite(url: String): List<Map<String, String>> = coroutineScope {
  withContext(Dispatchers.IO) {
    val document = Jsoup.connect(url).get()
    val table = document.select("table.wikitable").firstOrNull()
    val rows = mutableListOf<Map<String, String>>()

    table?.select("tr")?.drop(1)?.forEach { row ->
      val cells = row.select("td")
      if (cells.isNotEmpty()) {
        val rowMap = mutableMapOf<String, String>()
        val location = cells.getOrNull(1)?.select("a")?.text()?.trim() ?: cells.getOrNull(1)?.text()?.trim() ?: ""
        val population = cells.getOrNull(2)?.text()?.replace(",", "")?.trim() ?: ""
        val percent = cells.getOrNull(3)?.text()?.trim() ?: ""
        val date = cells.getOrNull(4)?.text()?.trim() ?: ""
        rowMap["Location"] = location
        rowMap["Population"] = population
        rowMap["% of World"] = percent
        rowMap["Date"] = date
        rows.add(rowMap)
      }
    }

    rows
  }
}




fun visualizeDataFrame(dataFrame: DataFrame<*>) {
  val frame = JFrame("Enhanced DataFrame Visualization")
  frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
  frame.setSize(1200, 800)
  frame.layout = BorderLayout()
  val tableModel = DefaultTableModel()
  dataFrame.columnNames().forEach { columnName ->
    tableModel.addColumn(columnName)
  }
  dataFrame.rows().forEach { row ->
    val rowValues = row.values().flatMap {
      if (it is List<*>) it.map { element -> element.toString() }
      else listOf(it.toString())
    }.toTypedArray()
    tableModel.addRow(rowValues)
  }

  val table = JTable(tableModel)
  table.fillsViewportHeight = true
  table.setShowGrid(true)
  table.gridColor = Color.LIGHT_GRAY
  val sorter = TableRowSorter(tableModel)
  table.rowSorter = sorter
  val filterPanel = JPanel(BorderLayout())
  filterPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
  val filterField = JTextField(20)
  filterField.margin = Insets(5, 5, 5, 5)
  val filterButton = JButton("Apply Filter")
  filterButton.isFocusPainted = false
  filterButton.background = Color(0x2C7E7E)
  filterButton.foreground = Color.BLACK


  val exportButton = JButton("Export to CSV")
  exportButton.isFocusPainted = false
  exportButton.background = Color(0x2C7E7E)
  exportButton.foreground = Color.BLACK

  val columnPanel = JPanel(GridLayout(0, 1))
  columnPanel.border = BorderFactory.createTitledBorder("Search by Columns")
  val columnCheckBoxes = mutableMapOf<String, JCheckBox>()
  dataFrame.columnNames().forEach { columnName ->
    val checkBox = JCheckBox(columnName, true)
    checkBox.background = Color.WHITE
    columnCheckBoxes[columnName] = checkBox
    columnPanel.add(checkBox)
  }
  columnPanel.background = Color(0xF0F8FF)
  filterButton.addActionListener {
    val filterText = filterField.text
    if (filterText.isEmpty()) {
      sorter.rowFilter = null
    } else {
      val activeColumns = columnCheckBoxes.filterValues { it.isSelected }.keys
      sorter.rowFilter = object : RowFilter<DefaultTableModel, Int>() {
        override fun include(entry: Entry<out DefaultTableModel, out Int>): Boolean {
          return activeColumns.any { columnName ->
            val columnIndex = dataFrame.columnNames().indexOf(columnName)
            if (columnIndex != -1) {
              val cellValue = entry.getStringValue(columnIndex)
              cellValue.contains(filterText, ignoreCase = true)
            } else {
              false
            }
          }
        }
      }
    }
  }
  exportButton.addActionListener { exportToCSV(dataFrame, "output.csv") }
  val filterBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
  filterBar.background = Color(0xF0F8FF)
  filterBar.add(JLabel("Search:"))
  filterBar.add(filterField)
  filterBar.add(filterButton)
  filterBar.add(exportButton)
  val mainPanel = JPanel(BorderLayout())
  mainPanel.add(JScrollPane(table), BorderLayout.CENTER)
  mainPanel.add(filterBar, BorderLayout.NORTH)
  mainPanel.add(JScrollPane(columnPanel), BorderLayout.WEST)
  frame.add(mainPanel)
  frame.isVisible = true
}

fun exportToCSV(dataFrame: DataFrame<*>, filePath: String) {
  val file = File(filePath)
  file.printWriter().use { writer ->
    writer.println(dataFrame.columnNames().joinToString(","))
    dataFrame.rows().forEach { row ->
      val rowValues = row.values().flatMap {
        if (it is List<*>) it.map { element -> element.toString() }
        else listOf(it.toString())
      }
      writer.println(rowValues.joinToString(","))
    }
  }
  JOptionPane.showMessageDialog(null, "Data exported to $filePath")
}
